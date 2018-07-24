

/*Description:receivebuffer                                *
 *Author     :layrong                                      *
 *Data       :2014-4-14                                    *
 *Mail       :nilayrong@163.com                            *
 ***********************************************************/

#include"receivebuffer.h"
#include<iostream>
#include<list>
using namespace std;

/*
 *Author:layrong
 *Name:
 *Description:判断缓冲是否满
 */

bool ReceiveBuffer::IsFull()
{

    if(buffer->size()==MAX)
    {

        return true;
    }
    else return false;


}

/*
 *Author:layrong
 *Name:
 *Description:判断缓冲是否空
 */
bool ReceiveBuffer::IsEmpty()
{

    return buffer->empty();

}

/*
 *Author:layrong
 *Name:
 *Description:向缓冲中放入一条记录
 */
void ReceiveBuffer::Add(struct Record *record)
{

    buffer->push_back(record);

}

/*
 *Author:layrong
 *Name:
 *Description:从缓冲中取出一条记录
 */
struct Record* ReceiveBuffer:: Get()
{

    struct Record*record=buffer->front();
    buffer->pop_front();
    return record;
}


ReceiveBuffer::ReceiveBuffer()
{
    buffer=new list<struct Record*>();

}

