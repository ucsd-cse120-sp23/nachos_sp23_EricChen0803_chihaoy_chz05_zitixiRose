#include "syscall.h"

int main(int argc, char *argv[]){
    char *prog1 = "write10.coff";
    char *prog2 = "write1.coff";
    int status1, status2 = 0;

    int pid1 = exec(prog1, 0, 0);
    join(pid1, &status1);
    if(pid1 == -1){
        printf('FAILED 1');
        exit(-1);
    }
    int pid2 = exec(prog2, 0, 0);
    if(pid2 == -1){
        printf('FAILED 2');
        exit(-1);
    }

    // printf("Inter FIRST JOIN\n");
    
    // printf("AFTER FIRST JOIN\n");
    join(pid2, &status2);
    // printf("AFTER SECOND JOIN\n");

   
}