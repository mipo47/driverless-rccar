#!/usr/bin/env python3
# -*- coding: utf-8 -*-


import socket
import sys
import os
import datetime
import cv2
import numpy as np
import win32api as wapi

APP_NAME = "stream"
HOST = "0.0.0.0"
PORT = 5000

def main():
    cap = cv2.VideoCapture(0)
    while True:
        ret, frame = cap.read()
        cv2.imshow("keys", frame)

        key = cv2.waitKey(1)
        if key != -1:
            if key == ord('q'):
                break
            elif 48 <= key <= 57: # keys 0-9
                number = key - 48
                print("Number", number)

        if wapi.GetAsyncKeyState(ord('A')):
            print("left")
        if wapi.GetAsyncKeyState(ord('D')):
            print("right")
        if wapi.GetAsyncKeyState(ord('W')):
            print("forward")
        if wapi.GetAsyncKeyState(ord('S')):
            print("backward")

if __name__ == '__main__':
    main()
