import cv2
import numpy as np
from stream_udp import *
import pandas as pd
import tensorflow as tf
from collections import deque

CHECKPOINT_DIR = '/tmp/deeprccar'
METAGRAPH_FILE = CHECKPOINT_DIR + '/deeprccar-model.meta'
PATH = 'datasets/X_2017-10-27_04_44_36'
# PATH = 'datasets/Y_2017-10-27_04_53_53'
RATIO = 1.0 / 8

df = pd.read_csv(PATH + "/header.csv")
timestamps = []
fps = 25.0

video_writer = cv2.VideoWriter_fourcc(*'DIVX')
out = cv2.VideoWriter('player.avi', video_writer, fps, (720, 480))

image_queue = deque()
next_state = None
with tf.Session() as sess:
    saver = tf.train.import_meta_graph(METAGRAPH_FILE)
    ckpt = tf.train.latest_checkpoint(CHECKPOINT_DIR)
    saver.restore(sess, ckpt)
    input_images = tf.get_collection("input_images")[0]
    prev_state = tf.get_collection("prev_state")
    next_state_op = tf.get_collection("next_state")
    prediction_op = tf.get_collection("predictions")[0]
    lookback_length_op = tf.get_collection("lookback_length")[0]
    stats_op = tf.get_collection("stats")

    for index, row in df.iterrows():
        with open(PATH + "/{}.jpg".format(row["image_id"].astype(np.int32)), 'rb') as f:
            img = f.read()
        np_arr = np.frombuffer(img, dtype=np.uint8)
        frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        image = cv2.resize(frame, None, fx=RATIO, fy=RATIO)

        if len(image_queue) == 0:
            lookback_length = sess.run(lookback_length_op)
            image_queue.extend([image] * (lookback_length + 1))
            mean, stddev = sess.run(stats_op)
            print("Training mean: {}\nTraining stddev: {}"
                  .format(mean, stddev))
        else:
            image_queue.popleft()
            image_queue.append(image)

        image_sequence = np.stack(image_queue)
        feed_dict = {
            input_images: image_sequence,
        }

        if next_state is not None:
            feed_dict.update(dict(zip(prev_state, next_state)))

        next_state, prediction = sess.run([next_state_op,
                                           prediction_op],
                                          feed_dict=feed_dict)

        predicted = np.round(prediction).flatten().astype(np.int32)
        speed_cmd = int(lerp(predicted[1], -180, 0, 250, SPEED_MIN, SPEED_NEUTRAL, SPEED_MAX))
        steering_cmd = int(lerp(predicted[0], -100, 0, 100, STEERING_MIN, STEERING_NEUTRAL, STEERING_MAX))
        cv2.line(frame, (370, 300), (370 - steering_cmd, 300 - speed_cmd), (255, 0, 255), 3, cv2.LINE_AA)
        cv2.line(frame, (370, 300), (370 - steering_cmd, 300 - speed_cmd), (0, 0, 0), 2, cv2.LINE_AA)

        Stream.draw(row.values[2:], frame, fps)
        out.write(frame)

out.release()
cv2.destroyAllWindows()