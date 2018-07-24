#include<iostream>
#include<vector>
#include<fstream>
#include<string>
#include<stdio.h>
#include<vector>
#include<map>
#include<algorithm>
#include<unordered_set>
#include<unordered_map>
#include<sys/types.h>
#include<sys/stat.h>
#include<time.h>
#include<math.h>
#include<stdlib.h>
#include<io.h>
#include<string.h>
#include"ProcessData.h"
using namespace std;


void getResult(string key, vector<int> &intsets, ofstream &os) {
	sort(intsets.begin(), intsets.end());
	os << key << "," << intsets[((intsets.size() - 1) * 90) / 100] << endl;
}

void getBin(string input, string output) {
	remove(output.c_str());
	unordered_map<string, vector<int>> m;
	ifstream is(input, std::ios::in);
	ofstream os(output);
	string s;
	while (getline(is, s)) {

		int pos1 = s.find_first_of(',');
		int pos2 = s.find_first_of(',', pos1 + 1);
		int rssi = stoi(s.substr(0, pos1)) / 2 * 2;
		int au = (stoi(s.substr(pos1 + 1, pos2 - pos1 - 1))) / 2 * 2;

		m[to_string(rssi) + "," + to_string(au)].push_back(
				(stoi(s.substr(pos2 + 1)) + 1000) / 1000);

	}
	is.close();
	unordered_map<string, vector<int>>::iterator iter = m.begin();
	while (iter != m.end()) {
		getResult(iter->first, iter->second, os);
		iter++;
	}
	os.close();
}

#define Randmod(x) rand()%x
/*对双频段数据做笛卡尔积并划分为训练集测试集，var表示模糊算法中的模糊间隔（*1000）*/
void flagTrainingSet2(int var, string input1, string input2, string output1,
		string output2) {
	cout << "var is " << var << endl;
	//output1 = output1 + to_string(var) + ".csv";
	//output2 = output2 + to_string(var) + ".csv";
	remove(output1.c_str());
	remove(output2.c_str());
	unordered_map<string, int> m;
	ifstream is1(input1, std::ios::in);
	ifstream is2(input2, std::ios::in);
	ofstream os1(output1);
	ofstream os2(output2);
	string s;
	os1 << "rssi_2.4,au_2.4,rssi_5,au_5,delay_2.4,delay_5,choose" << endl;
	os2 << "rssi_2.4,au_2.4,rssi_5,au_5,delay_2.4,delay_5,choose" << endl;

	while (getline(is1, s)) {
		int pos = s.find_first_of(',');
		int pos2 = s.find_first_of(',', pos + 1);
		int rssi = stoi(s.substr(0, pos));
		int au = stoi(s.substr(pos + 1, pos2 - pos - 1));
		m[to_string(rssi) + "," + to_string(au)] = stoi(s.substr(pos2 + 1));
	}
	is1.close();
	int trainSizeClass0 = 0, trainSizeClass1 = 0, trainSizeClass2 = 0;
	int testSizeClass0 = 0, testSizeClass1 = 0, testSizeClass2 = 0;
	srand((unsigned) time(NULL));
	while (getline(is2, s)) {
		int pos = s.find_first_of(',');
		int pos2 = s.find_first_of(',', pos + 1);
		int rssi = stoi(s.substr(0, pos));
		int au = stoi(s.substr(pos + 1, pos2 - pos - 1));
		int delay = stoi(s.substr(pos2 + 1));
		int index = 0;
		int val;
		for (auto a : m) {	//3:1 训练集 测试集
			if (index % 4 == 0) {
				val = Randmod(4);
				index = 0;
			}
			if (index == val) {
				os2 << a.first << "," << rssi << "," << au << "," << a.second
						<< "," << delay << ",";
				if (abs(a.second - delay) < var * 1000) {
					testSizeClass2++;
					os2 << 2 << endl;
				} else if (delay <= a.second) {
					testSizeClass1++;
					os2 << 1 << endl;
				} else {
					testSizeClass0++;
					os2 << 0 << endl;
				}
			} else {
				os1 << a.first << "," << rssi << "," << au << "," << a.second
						<< "," << delay << ",";
				if (abs(a.second - delay) < var * 1000) {
					trainSizeClass2++;
					os1 << 2 << endl;
				} else if (delay <= a.second) {
					trainSizeClass1++;
					os1 << 1 << endl;
				} else {
					trainSizeClass0++;
					os1 << 0 << endl;
				}
			}
			index++;
		}
	}

	cout << "测试集 class 0 size:" << testSizeClass0 << " class1 size:"
			<< testSizeClass1 << " class2 size:" << testSizeClass2
			<< "class2 ratio:"
			<< double(testSizeClass2)
					/ (double) (testSizeClass0 + testSizeClass1 + testSizeClass2)
			<< endl;

	cout << "训练集 class 0 size:" << trainSizeClass0 << " class1 size:"
			<< trainSizeClass1 << " class2 size:" << trainSizeClass2
			<< "class2 ratio:"
			<< double(trainSizeClass2)
					/ (double) (trainSizeClass0 + trainSizeClass1
							+ trainSizeClass2) << endl;
	is2.close();
	os1.close();
	os2.close();
}
