#include "syscall.h"
#include "stdio.h"

#define MAXOPENFILES 14

// menu to choose which test to run
enum TESTOPTION {CREAT, OPEN, READ_WRITE};

/** tests the following:
 *  - whether creat successfully creates a file
 *  - whether creat returns -1 if the OpenFile list is full
 */
void creatTest() {
  // test whether creat successfully creates a file
  char* fileName = "creatMe.txt\0";
  int success = creat(fileName);
  if(success != -1) {
    printf("creat successfully creates a file!\n");
  }
  else {
    printf("creat unsuccessfully creates a file!\n");
    return;
  }

  // test whether creat returns -1 if the OpenFile list is full
  int i;
  for(i = 0; i < MAXOPENFILES - 1; i++) {
    if(creat(fileName) == -1) {
      printf("creat fails while creating more OpenFiles in the list! Index = %d\n", i);
      return;
    }
  }
  success = creat(fileName);
  if(success == -1) {
    printf("creat successfully returns -1!\n");
  }
  else {
    printf("creat unsuccessfully returns -1!\n");
    return;
  }

  printf("Creat Test complete!\n");
}

/** tests the following:
 *  - whether creat successfully creates a file
 *  - whether open successfully opens the created file
 *  - whether open unsuccessfully opens nonexistent file
 *  - whether open returns -1 if the OpenFile list is full
 */  
void openTest() {
  // test whether creat successfully creates a file
  char* fileName = "creatMe.txt\0";
  int success = creat(fileName);
  if(success != -1) {
    printf("creat successfully creates a file!\n");
  }
  else {
    printf("creat unsuccessfully creates a file!\n");
    return;
  }

  // test whether open successfully opens the created file
  success = open(fileName);
  if(success != -1) {
    printf("open successfully opens a file!\n");
  }
  else {
    printf("open unsuccessfully opens a file!\n");
    return;
  }

  // test whether open unsuccessfully opens nonexistent file
  fileName = "cannotOpenMe.txt\0";
  success = open(fileName);
  if(success == -1) {
    printf("open successfully fails to open nonexistent file!\n");
  }
  else {
    printf("open unsuccessfully fails to open nonexistent file!\n");
    return;
  }

  // test whether open returns -1 if the OpenFile list is full
  int i;
  fileName = "creatMe.txt\0";
  for(i = 0; i < MAXOPENFILES - 2; i++) {
    if(open(fileName) == -1) {
      printf("open fails while opening more OpenFiles in the list!\n");
      return;
    }
  }
  success = open(fileName);
  if(success == -1) {
    printf("open successfully returns -1!\n");
  }
  else {
    printf("open unsuccessfully returns -1!\n");
    return;
  }

  printf("Open Test complete!\n");
}

/** tests the following:
 *  - whether open successfully opens a file
 *  - whether read successfully reads a file and writes to the disk
 *  - whether stdout successfully prints out from the read file
 */
void read_writeTest() {
  // test whether open successfully opens the created file
  char* readFileName = "txt2Read.txt\0";
  char* writeFileName = "txt2Write.txt\0";
  int readFD = open(readFileName);
  int writeFD = open(writeFileName);
  if(readFD != -1 && writeFD) {
    printf("open successfully opens read/write files!\n");
  }
  else {
    printf("open unsuccessfully opens read/write files!\n");
    return;
  }

  // test whether read successfully reads a file and writes to the disk
  void* buffer;
  int count = 50;
  int transferred;
  do {
    transferred = read(readFD, buffer, count);
  } while(write(writeFD, buffer, transferred) > 0);
  printf("Finished reading and writing! Check out %s and %s for sanity check\n", readFileName, writeFileName);

  // test whether stdout successfully prints out from the read file
  readFD = open(readFileName);
  writeFD = 1;  // stdout
  if(readFD != -1 && writeFD) {
    printf("open successfully opens read/write files!\n");
  }
  else {
    printf("open unsuccessfully opens read/write files!\n");
    return;
  }
  do {
    transferred = read(readFD, buffer, count);
  } while(write(writeFD, buffer, transferred) > 0);
  printf("Finished reading and writing! Check out %s and output for sanity check\n", readFileName);

  printf("Read & Write Test complete!\n");
}


int main(int argc, char** argv) {
  // get test # (regarding test #'s, check enum above)
  if(argc == 0) {
    printf("Test # was not chosen\n");
    halt(); // "exit" function for task1
  }
  int test = atoi(argv[0]);

  switch(test) {
    case CREAT:
      printf("Chose CREAT test!\n");
      creatTest();
      break;
    case OPEN:
      printf("Chose OPEN test!\n");
      openTest();
      break;
    case READ_WRITE:
      printf("Chose READ_WRITE test!\n");
      read_writeTest();
      break;
    default:
      printf("Chose not supported test!\n");
  }

  halt(); // "exit" function for task1
}
