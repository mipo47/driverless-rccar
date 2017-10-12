#!/usr/bin/env python3
# -*- coding: utf-8 -*-


import socket
import sys
import os
import datetime
import cv2
import numpy as np

APP_NAME = "stream"
HOST = "0.0.0.0"
PORT = 5000

def main():
    mySocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    mySocket.bind((HOST, PORT))
    mySocket.listen(1)
    image_id = 0
    while True:
        print("Waiting for a new connection")
        conn, addr = mySocket.accept()
        timestep = 0
        has_connection = True
        timestamps = []
        try:
            print("Connection from: " + str(addr))
            while True:
                timestep += 1
                image_id += 1
                buff = b''
                while True:
                    c = conn.recv(1)
                    if c == b'$':
                        print("Client asked to disconnect")
                        conn.close()
                        has_connection = False
                        break
                    elif c == b'[':
                        continue
                    elif c == b']':
                        break
                    else:
                        buff += c

                if not has_connection:
                    break

                decoded_buff = buff.decode()
                header = decoded_buff.split(';')
                arduino_online = bool(int(header[0]))
                speed_cmd = int(header[1])
                steering_cmd = int(header[2])

                speed = float(header[3])
                steering = float(header[4])
                size = int(header[5])

                # print(arduino_online, timestep, speed_cmd, steering_cmd, speed, steering)

                img = bytearray()
                size_left = size
                while size_left > 0:
                    chunk = conn.recv(size_left)
                    img.extend(chunk)
                    size_left -= len(chunk)

                np_arr = np.frombuffer(img, dtype=np.uint8)
                frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

                for i in range(6, 6 + 3*3):
                    value = float(header[i])
                    x = int(720 / 2)
                    y = int(20 + (i-6) * 10)
                    if i < 9: # ACCELEROMETER
                        color = (0, 255, 0)
                        x_value = int(x + value * 10)
                    elif i < 12: # GYROSCOPE
                        color = (0, 0, 255)
                        x_value = int(x + value * 20)
                    else: # MAGNETIC_FIELD
                        color = (255, 0, 0)
                        x_value = int(x + value * 2)

                    x_value = np.clip(x_value, 0, 720)
                    cv2.line(frame, (x, y), (x_value, y), color, 2)

                cv2.imshow(APP_NAME, frame)

                timestamp = datetime.datetime.now().timestamp()
                if len(timestamps) > 0:
                    speed = (timestamp - timestamps[0]) * 1000.0 / len(timestamps)
                    fps = 1000.0 / speed
                    print("{:.2f} ms, {:.1f} fps".format(speed, fps), decoded_buff)

                timestamps.append(timestamp)
                while len(timestamps) > 30:
                    timestamps.pop(0)

                key = cv2.waitKey(1) & 0xFF
                if key == ord('q'):  # Hit `q` to exit
                    break


        except KeyboardInterrupt:
            print("Server asked to disconnect")
            conn.close()

if __name__ == '__main__':
    main()
