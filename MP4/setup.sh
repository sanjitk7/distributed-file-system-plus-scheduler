# Make sure code is up-to-date on VM
git pull origin main
git clean -fd

# Dependency installation
# python3 -m pip install --upgrade pip --user
# python3 -m pip install --upgrade Pillow --user
# sudo yum install -y python3-devel
# sudo yum install -y zlib
# sudo yum install -y libjpeg-devel
# pip3 install --user pillow --no-cache-dir
# pip3 install --user torchvision --no-cache-dir

clear

# Run the file_server
python3 file_server.py
