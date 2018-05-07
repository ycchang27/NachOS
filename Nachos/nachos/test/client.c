#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv) {
  printf("Client\n");
  // Get the arguments
  if(argc != 2) {
    printf("Invalid number of arguments\n");
    halt(); // "exit" function
  }
  int host = atoi(argv[0]);
  int port = atoi(argv[1]);

  // Get file descriptor for this connection
  int fd = connect(host, port);
  char* buffer = "123\0";
  int bytesSent = write(fd, (void*)buffer, 3);
  printf("bytesSent = %d\n", bytesSent);
  printf("client buffer = %s\n", buffer);
  while(true) {
     getchar();
  }
}