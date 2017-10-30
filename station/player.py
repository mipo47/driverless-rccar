import cv2
import numpy as np
from stream_udp import Stream
import pandas as pd

PATH = 'datasets/X_2017-10-27_04_44_36'

df = pd.read_csv(PATH + "/header.csv")[500:]
timestamps = []
fps = 25.0

video_writer = cv2.VideoWriter_fourcc(*'DIVX')
out = cv2.VideoWriter('player.avi', video_writer, fps, (720, 480))

for index, row in df.iterrows():
    with open(PATH + "/{}.jpg".format(row["image_id"].astype(np.int32)), 'rb') as f:
        img = f.read()
    np_arr = np.frombuffer(img, dtype=np.uint8)
    frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

    timestamp = float(row["timestamp"])
    if len(timestamps) > 0:
        fps = len(timestamps) / (timestamp - timestamps[0] + 1e-6)

    timestamps.append(timestamp)
    while len(timestamps) > 50:
        timestamps.pop(0)

    Stream.draw(row.values[2:], frame, fps)
    out.write(frame)

out.release()
cv2.destroyAllWindows()