
FROM ubuntu:24.04

RUN apt update

RUN apt install python3 python3-pip curl cmake git -y

COPY requirements.txt /requirements.txt

RUN pip install --break-system-packages -r requirements.txt
