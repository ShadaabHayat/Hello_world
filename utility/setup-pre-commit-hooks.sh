#!/bin/bash

# Function to install pre-commit using the OS package manager or Poetry/pip
install_pre_commit() {
  local os=$(uname -s)
  case "$os" in
    Linux)
      if command -v apt &> /dev/null; then
        sudo apt update
        sudo apt install pre-commit -y
      elif command -v dnf &> /dev/null; then
        sudo dnf install pre-commit -y
      elif command -v pacman &> /dev/null; then
        sudo pacman -S pre-commit --noconfirm
      else
        echo "Unsupported package manager."
        install_with_poetry_or_pip
      fi
      ;;
    Darwin)
      if command -v brew &> /dev/null; then
        brew install pre-commit
      else
        echo "Brew not found."
        install_with_poetry_or_pip
      fi
      ;;
    MINGW*)
      echo "Installing on Windows."
      install_with_poetry_or_pip
      ;;
    *)
      echo "Unsupported OS."
      install_with_poetry_or_pip
      ;;
  esac
}

# Function to install with Poetry if available, otherwise use pip
install_with_poetry_or_pip() {
  if command -v poetry &> /dev/null; then
    echo "Installing with Poetry..."
    poetry add pre-commit --group dev
  else
    echo "Poetry not found. Installing with pip..."
    pip install pre-commit
  fi
}

# Install pre-commit if not installed
if ! command -v pre-commit &> /dev/null; then
  echo "Installing pre-commit..."
  install_pre_commit
fi

# Install the hooks
echo "Installing pre-commit hooks..."
pre-commit install
chmod +x check-staged-java.sh
chmod +x check-all-files.sh
