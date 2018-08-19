import cv2
import numpy as np
from stream_udp import Stream
import pandas as pd

# PATH = 'datasets/maxima/21784'
# PATH = 'datasets/maxima/17947'
PATH = 'datasets/maxima/10816'

RATIO = 1.0 # 1.0 / 8

df = pd.read_csv(PATH + "/filtered.csv")
timestamps = []
fps = 25.0

video_writer = cv2.VideoWriter_fourcc(*'DIVX')
out = cv2.VideoWriter('player.avi', video_writer, fps, (720, 480))

for index, row in df.iterrows():
    with open(PATH + "/{}.jpg".format(row["image_id"].astype(np.int32)), 'rb') as f:
        img = f.read()
    np_arr = np.frombuffer(img, dtype=np.uint8)
    frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    frame = cv2.resize(frame, None, fx=RATIO, fy=RATIO)

    timestamp = float(row["timestamp"])
    if len(timestamps) > 0:
        fps = 1000 * len(timestamps) / (timestamp - timestamps[0] + 1e-6)

    timestamps.append(timestamp)
    while len(timestamps) > 50:
        timestamps.pop(0)

    Stream.draw(row.values[2:], frame, fps)
    out.write(frame)

out.release()
cv2.destroyAllWindows()