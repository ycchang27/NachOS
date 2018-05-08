#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv) {
  printf("Host\n");
  // Get the arguments
  if(argc != 3) {
    printf("Invalid number of arguments\n");
    halt(); // "exit" function for task1
  }
  int port = atoi(argv[0]);
  int size1 = atoi(argv[1]);
  int size2 = atoi(argv[2]);

  // Get file descriptor for this connection
  int fd1 = accept(port);
  int fd2 = accept(port);
  printf("accept uses fd1 = %d\n",fd1);
  printf("accept uses fd2 = %d\n",fd2);

  // Receive buffer
  void* buffer;
  int bytesRead = read(fd1, buffer, size1);
  while(bytesRead == -1) {
    bytesRead = read(fd1, (void*)buffer, size1);
  }
  printf("\nbytesRead = %d\n", bytesRead);
  bytesRead = read(fd2, (void*)buffer, size2);
  while(bytesRead == -1) {
    bytesRead = read(fd2, (void*)buffer, size2);
  }
  printf("\nbytesRead = %d\n", bytesRead);
}