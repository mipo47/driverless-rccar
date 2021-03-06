import socket
import select
import sys
import os
import datetime
import cv2
import numpy as np
import win32api as wapi

APP_NAME = "stream"
HOST = "0.0.0.0"
PORT = 5000
TIMEOUT = 5  # seconds

SPEED_NEUTRAL = 1470
SPEED_MIN = 1000
SPEED_MAX = 1800
STEERING_NEUTRAL = 1420
STEERING_MIN = STEERING_NEUTRAL - 530  # right
STEERING_MAX = STEERING_NEUTRAL + 530  # left
TURN_AMOUNT = 0.005
ACCELERATE_AMOUNT = 0.002
BRAKE_AMOUNT = ACCELERATE_AMOUNT

def lerp(value, min, neutral, max):
    result = neutral
    if value > 0:
        result = neutral * (1.0 - value) + max * value
    elif value < 0:
        result = neutral * (1.0 + value) + min * -value
    return np.clip(result, min, max)

class Stream:
    def __init__(self):
        self.socket, self.ip = None, None

        self.image_id = 0
        self.keys = [
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'F',
            'C', 'M',
            'W', 'A', 'S', 'D'
        ]
        self.key_down = {}

        self.control = False
        self.speed = 0  # -1 to 1
        self.steering = 0  # -1 to 1

    def start(self, start_image_id=0):
        self.image_id = start_image_id

        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.bind((HOST, PORT))
        server_socket.listen(1)
        while True:
            print("Waiting for a new connection")
            self.socket, self.ip = server_socket.accept()
            print("Connection from: " + str(self.ip))
            try:
                self.connection(self.socket)
            except (ConnectionResetError, ConnectionAbortedError):
                print("Client closed connection")
                if self.socket is None:
                    self.socket.close()


    def send_command(self, command):
        if self.socket is None:
            print("Simulate send", command, self.steering)
        else:
            command = "[" + command + "]"
            b = bytearray()
            b.extend(map(ord, command))
            self.socket.send(b)

    def key_check(self):
        key_presssed = {}
        for key in self.keys:
            old_down = self.key_down[key] if key in self.key_down else False
            new_down = wapi.GetAsyncKeyState(ord(key))
            self.key_down[key] = new_down

            # key was just released
            key_presssed[key] = old_down and not new_down

        for q in range(10):
            if wapi.GetAsyncKeyState(ord(str(q))):
                quality = q * 10 if q > 0 else 5
                self.send_command('Q;{}'.format(quality))
                break

        if key_presssed['F']:
            self.send_command("F")

        if key_presssed['C']:
            self.send_command("A;C")
            self.control = True
        elif key_presssed['M']:
            self.send_command("A;M")
            self.control = False

        if self.control:
            if self.key_down['A']:
                self.steering += TURN_AMOUNT / (1.0 + np.abs(self.speed))
            if self.key_down['D']:
                self.steering -= TURN_AMOUNT / (1.0 + np.abs(self.speed))

            if self.key_down['S']:
                self.speed -= BRAKE_AMOUNT
            elif self.key_down['W']:
                self.speed += ACCELERATE_AMOUNT

            self.speed = np.clip(self.speed, -1.0, 1.0)
            self.steering = np.clip(self.steering, -1.0, 1.0)

            self.steering *= 0.995
            self.speed *= 0.995

    def connection(self, conn):
        timestep = 0
        has_connection = True
        timestamps = []
        fps = 0
        while True:
            timestep += 1
            self.image_id += 1

            buff = b''
            conn.setblocking(False)
            start_recv = datetime.datetime.now().timestamp()
            while True:
                self.key_check()

                ready_to_read, ready_to_write, in_error = select.select([conn], [], [], 0.1)

                if datetime.datetime.now().timestamp() - start_recv > (TIMEOUT if timestep > 1 else TIMEOUT * 5):
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

            if self.control:
                speed = lerp(self.speed, SPEED_MIN, SPEED_NEUTRAL, SPEED_MAX)
                steering = lerp(self.steering, STEERING_MIN, STEERING_NEUTRAL, STEERING_MAX)
                self.send_command("A;{:.0f} {:.0f}".format(speed, steering))

            timestamp = datetime.datetime.now().timestamp()
            if len(timestamps) > 0:
                fps = len(timestamps) / (timestamp - timestamps[0])
                print(decoded_buff)

            timestamps.append(timestamp)
            while len(timestamps) > 50:
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

        # cv2.line(frame, (5, 5), (5 + int(distance * 2), 5), (255, 0, 255), 2)
        cv2.putText(frame,
                    "{:.0f}".format(fps), (10, 460),
                    cv2.FONT_HERSHEY_SIMPLEX, 1,
                    (255, 255, 255), 2, cv2.LINE_AA)

        cv2.imshow(APP_NAME, frame)
        cv2.waitKey(1)

if __name__ == '__main__':
    stream = Stream()
    stream.start()
    # while True:
    #     stream.key_check()