#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv) {
  printf("Client\n");
  // Get the arguments
  if(argc != 4) {
    printf("Invalid number of arguments\n");
    halt(); // "exit" function
  }
  int host = atoi(argv[0]);
  int port = atoi(argv[1]);
  char* buffer = argv[2];
  int size = atoi(argv[3]);

  // Get file descriptor for this connection
  int fd = connect(host, port);
  int bytesSent = write(fd, (void*)buffer, size);
  printf("bytesSent = %d\n", bytesSent);
  printf("client buffer = %s\n", buffer);
  while(true) {
  //    char c = getchar();
  //    printf("%d",c);
  }
}