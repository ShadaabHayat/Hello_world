FROM ubuntu:24.04

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      python3 python3-pip curl git build-essential \
 && rm -rf /var/lib/apt/lists/*

ENV POETRY_HOME="/opt/poetry" \
    POETRY_VERSION="1.8.1"
RUN curl -sSL https://install.python-poetry.org | python3 - \
 && ln -s $POETRY_HOME/bin/poetry /usr/local/bin/poetry

WORKDIR /app

COPY pyproject.toml poetry.lock ./
RUN poetry config virtualenvs.create false \
&& poetry install --no-interaction --no-ansi

COPY src/ ./src
COPY tests/ ./tests

CMD ["poetry", "run", "python3", "-m", "src.main"]
