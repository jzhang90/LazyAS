#ifndef RECEIVEBUFFER_H_
#define RECEIVEBUFFER_H_
#include<list>
#include"test.h"
#define MAX 100

using namespace std;


class ReceiveBuffer
{

private:

    list<struct Record*> * buffer;


public:

    ReceiveBuffer();

    bool IsEmpty();

    bool IsFull();

    void Add(struct Record *record);

    struct Record* Get();

};



#endif
