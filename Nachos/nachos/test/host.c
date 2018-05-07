#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv) {
  printf("Host\n");
  // Get the arguments
  if(argc != 1) {
    printf("Invalid number of arguments\n");
    halt(); // "exit" function for task1
  }
  int port = atoi(argv[0]);

  // Get file descriptor for this connection
  int fd1 = accept(port);
  printf("accept uses fd1 = %d\n",fd1);
  void* buffer = (void*)(3*sizeof(char));
  int bytesRead = read(fd1, buffer, 3);
  while(bytesRead == -1) {
    bytesRead = read(fd1, buffer, 3);
  }
  printf("bytesRead = %d\n", bytesRead);
  printf("host buffer = %s\n", (char*)buffer);
  //int fd2 = accept(port);'
}