#!/usr/bin/env python3
# -*- coding: utf-8 -*-


import socket
import sys
import os
import datetime
import cv2
import numpy as np


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
        start = round(datetime.datetime.now().timestamp() * 1000)
        has_connection = True
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
                speed_cmd = int(header[0])
                steering_cmd = int(header[1])
                speed = float(header[2])
                steering = float(header[3])
                size = int(header[4])

                # print(timestep, speed_cmd, steering_cmd, speed, steering, size)
                img = bytearray()
                size_left = size
                while size_left > 0:
                    chunk = conn.recv(size_left)
                    img.extend(chunk)
                    size_left -= len(chunk)

                np_arr = np.frombuffer(img, dtype=np.uint8)
                frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
                cv2.imshow('acquisition', frame)

                if (cv2.waitKey(1) & 0xFF) == ord('q'):  # Hit `q` to exit
                    break

                # timestamp in miliseconds
                timestamp = round(datetime.datetime.now().timestamp() * 1000)

                speed = (timestamp - start) / timestep
                fps = 1000.0 * timestep / (timestamp - start)
                print("{:.2f} ms, {:.1f} fps, size {}".format(speed, fps, size))

        except KeyboardInterrupt:
            print("Server asked to disconnect")
            conn.close()

if __name__ == '__main__':
    main()
