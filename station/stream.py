import socket
import select
import sys
import os
import datetime
import cv2
import numpy as np

APP_NAME = "stream"
HOST = "0.0.0.0"
PORT = 5000
TIMEOUT = 5  # seconds


class Stream:
    def start(self, start_image_id=0):
        self.image_id = start_image_id

        mySocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        mySocket.bind((HOST, PORT))
        mySocket.listen(1)
        while True:
            print("Waiting for a new connection")
            conn, addr = mySocket.accept()
            print("Connection from: " + str(addr))
            self.connection(conn)

    def connection(self, conn):
        timestep = 0
        has_connection = True
        timestamps = []
        fps = 0
        while True:
            timestep += 1
            self.image_id += 1

            start_recv = datetime.datetime.now().timestamp()
            buff = b''
            conn.setblocking(False)
            while True:
                ready_to_read, ready_to_write, in_error = select.select([conn], [], [], 0.1)

                if (datetime.datetime.now().timestamp() - start_recv > TIMEOUT):
                    print("Client is frozen, reconnecting...")
                    conn.close()
                    has_connection = False
                    break
                elif len(ready_to_read):
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
            distance = float(header[3])
            size = int(header[4])
            # print(arduino_online, timestep, speed_cmd, steering_cmd)

            img = bytearray()
            size_left = size
            while size_left > 0:
                conn.setblocking(True)
                chunk = conn.recv(size_left)
                img.extend(chunk)
                size_left -= len(chunk)

            np_arr = np.frombuffer(img, dtype=np.uint8)
            frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

            timestamp = datetime.datetime.now().timestamp()
            if len(timestamps) > 0:
                speed = (timestamp - timestamps[0]) * 1000.0 / len(timestamps)
                fps = 1000.0 / speed
                print("{:.2f} ms, {:.1f} fps".format(speed, fps), decoded_buff)

            timestamps.append(timestamp)
            while len(timestamps) > 30:
                timestamps.pop(0)

            Stream.draw(header, frame, fps)


    @staticmethod
    def draw(header, frame, fps):
        distance = float(header[3])

        SENSOR_INDEX = 5
        for i in range(SENSOR_INDEX, SENSOR_INDEX + 3 * 3):
            value = float(header[i])
            x = int(720 / 2)
            y = int(20 + (i - SENSOR_INDEX) * 10)
            if i < SENSOR_INDEX + 3:  # ACCELEROMETER
                color = (0, 255, 0)
                x_value = int(x + value * 10)
            elif i < SENSOR_INDEX + 6:  # GYROSCOPE
                color = (0, 0, 255)
                x_value = int(x + value * 20)
            else:  # MAGNETIC_FIELD
                color = (255, 0, 0)
                x_value = int(x + value * 2)

            x_value = np.clip(x_value, 0, 720)
            cv2.line(frame, (x, y), (x_value, y), color, 2)

        cv2.line(frame, (5, 5), (5 + int(distance * 2), 5), (255, 0, 255), 2)
        cv2.putText(frame,
                    "{:.0f}".format(fps), (10, 460),
                    cv2.FONT_HERSHEY_SIMPLEX, 1,
                    (255, 255, 255), 2, cv2.LINE_AA)

        cv2.imshow(APP_NAME, frame)
        cv2.waitKey(1)

if __name__ == '__main__':
    stream = Stream()
    stream.start()
