/*
 * pruning.cpp
 *
 *  Created on: 2016年12月28日
 *      Author: layrong
 */

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
#include<queue>
#include<vector>
using namespace std;
typedef struct TreeNode {
	struct TreeNode *left;
	struct TreeNode *right;
	struct TreeNode *parent;
	string condition;
	double valueCon;
	string relation;
	bool canPruning;
	int pos;
	int leaf; //-1 0 1 2
	TreeNode() {
		left = NULL;
		right = NULL;
		parent = NULL;
		leaf = -1;
		pos = -1;
		valueCon = 0;
		canPruning = false;
	}
} TreeNode;
//层序遍历
vector<vector<string>> levelOrder(TreeNode* root) {
	vector<vector<string>> result;
	if (root == NULL)
		return result;
	queue<TreeNode*> q;
	q.push(root);
	TreeNode *endLevel = root;
	vector<string> curLevel;
	while (!q.empty()) {
		TreeNode *tmp = q.front();
		q.pop();
		curLevel.push_back(tmp->condition);
		if (tmp->left != NULL) {
			q.push(tmp->left);
		}
		if (tmp->right != NULL) {
			q.push(tmp->right);
		}
		if (tmp == endLevel) {
			result.push_back(curLevel);
			curLevel.clear();
			endLevel = q.back(); //当前层遍历完毕，则下一层也恰好全部入队，因此队尾即为下一层的结束标志
		}
	}
	return result;

}
//获取叶节点深度之和
void getLeafDepth(TreeNode* root, int &curDepth, int &totalDepth,
		int &totalLeafSize) {
	if (root->left == NULL && root->right == NULL) {
		totalDepth += curDepth;
		totalLeafSize++;
		return;
	}
	if (root->left != NULL) {
		curDepth++;
		getLeafDepth(root->left, curDepth, totalDepth, totalLeafSize);
		curDepth--;
	}
	if (root->right != NULL) {
		curDepth++;
		getLeafDepth(root->right, curDepth, totalDepth, totalLeafSize);
		curDepth--;
	}
}
#define Randmod(x) rand()%x
/*使用构建的决策树对数据集进行测试*/
int doTest(string input, TreeNode* root, string output) {
	ifstream is(input, std::ios::in);
	remove(output.c_str());
	ofstream os(output);
	string s;
	int totalDepth = 0;
	int totalSize = 0;
	int right = 0;
	getline(is, s);
	srand((unsigned) time(NULL));
	os << "leaf,flag,delay24,delay5,random,trueChoose,other" << endl;
	while (getline(is, s)) {
		int pos1 = s.find_first_of(',');
		int pos2 = s.find_first_of(',', pos1 + 1);
		int pos3 = s.find_first_of(',', pos2 + 1);
		int pos4 = s.find_first_of(',', pos3 + 1);
		int pos5 = s.find_first_of(',', pos4 + 1);
		int pos6 = s.find_first_of(',', pos5 + 1);
		int rssi_24 = stoi(s.substr(0, pos1));
		int au_24 = stoi(s.substr(pos1 + 1, pos2 - pos1 - 1));
		int rssi_5 = stoi(s.substr(pos2 + 1, pos3 - pos2 - 1));
		int au_5 = stoi(s.substr(pos3 + 1, pos4 - pos3 - 1));
		int delay_24 = stoi(s.substr(pos4 + 1, pos5 - pos4 - 1));
		int delay_5 = stoi(s.substr(pos5 + 1, pos6 - pos5 - 1));
		int flag = stoi(s.substr(pos6 + 1));
		TreeNode* cur = root;
		totalSize++;
		while (cur->left != NULL) {
			int curValCon;
			bool relation = false;
			if (cur->left->condition == "rssi_2.4") {
				curValCon = rssi_24;
			} else if (cur->left->condition == "rssi_5") {
				curValCon = rssi_5;
			} else if (cur->left->condition == "au_2.4") {
				curValCon = au_24;
			} else if (cur->left->condition == "au_5") {
				curValCon = au_5;
			}
			if (cur->left->relation == "<") {
				relation = curValCon < cur->left->valueCon;
			} else if (cur->left->relation == "<=") {
				relation = curValCon <= cur->left->valueCon;
			} else if (cur->left->relation == ">") {
				relation = curValCon > cur->left->valueCon;
			} else if (cur->left->relation == ">=") {
				relation = curValCon >= cur->left->valueCon;
			}
			if (relation) {
				cur = cur->left;
			} else {
				cur = cur->right;
			}
			totalDepth++;
		}
		os << cur->leaf << "," << flag << ",";
		if (cur->leaf == flag || flag == 2) {
			right++;
		}
		os << delay_24 << "," << delay_5 << ",";
		int v = Randmod(2);
		if (v == 0) {
			os << delay_24 << ",";
		} else {
			os << delay_5 << ",";
		}
		if (cur->leaf == 0)
			os << delay_24 << "," << delay_5;
		if (cur->leaf == 1)
			os << delay_5 << "," << delay_24;
		if (cur->leaf == 2) {
			if (delay_24 < delay_5) {
				os << delay_5 << "," << delay_24;
			} else {
				os << delay_24 << "," << delay_5;
			}
		}
		os << endl;
	}

	is.close();
	os.close();
	cout << "查找总深度： " << totalDepth << " 总查找次数： " << totalSize << endl;
	cout << "正确率：" << (double) (right) / (double) (totalSize) << endl;
	return totalDepth / totalSize;
}
/*根据clementine生成的决策树文件构建决策树*/
TreeNode* constructTree(string input) {
	unordered_map<int, TreeNode*> m;
	TreeNode *root = new TreeNode(), *last = root;
	root->condition = "root";
	root->parent = NULL;
	ifstream is(input, std::ios::in);
	string s;
	while (getline(is, s)) {
		int pos1 = s.find_first_of("ra");
		int pos3 = s.find_first_of(' ', pos1 + 1);
		int pos4 = s.find_first_of(' ', pos3 + 1);
		int pos5 = s.find_first_of(' ', pos4 + 1);
		TreeNode *tmp = new TreeNode();
		tmp->relation = s.substr(pos3 + 1, pos4 - pos3 - 1);
		tmp->condition = s.substr(pos1, pos3 - pos1);
		tmp->pos = pos1;
		tmp->valueCon = stod(s.substr(pos4 + 1, pos5 - pos4 - 1));
		if (last->pos >= pos1 && m.count(pos1)) { //重新找父节点
			last = m[pos1]->parent;
		}
		m[pos1] = tmp;
		if (last->left == NULL)
			last->left = tmp;
		else
			last->right = tmp;
		tmp->parent = last;
		unsigned int pos2 = s.find("=>");
		if (pos2 != string::npos) {
			tmp->leaf = stoi(s.substr(pos2 + 2, 2));
		}
		last = tmp;

	}
	is.close();
	return root;

}
/*判断能否剪支*/
bool canPruning(TreeNode *node) {
	if (node == NULL || node->canPruning)
		return true;
	return false;
}
/*对子树剪支*/
int doPruning(TreeNode* root) {
	if (root == NULL)
		return -1;
	int left = doPruning(root->left);
	int right = doPruning(root->right);
	if (canPruning(root->left) && canPruning(root->right)
			&& ((left | right) != 1)) {
		if (left == 1 || right == 1) {
			root->leaf = 1;
		} else if (left == 0 || right == 0) {
			root->leaf = 0;
		} else if (left == 2 || right == 2) {
			root->leaf = 2;
		}
		delete root->left;
		delete root->right;
		root->left = NULL;
		root->right = NULL;
		root->canPruning = true;
	}
	return root->leaf;
}

/*用于画盒图*/
void getBoxVal(string input) {
	ifstream is(input, std::ios::in);
	string s;
	vector<int> values;
	while (getline(is, s)) {
		int delay = stoi(s.substr(s.find_last_of(',') + 1));
		values.push_back(delay);
	}
	sort(values.begin(), values.end());
	
	cout << "values.size():" << values.size() << endl;
	cout << values[10 * values.size() / 100 - 1] / 1000 << " "
			<< values[25 * values.size() / 100 - 1] / 1000 << " "
			<< values[50 * values.size() / 100 - 1] / 1000 << " "
			<< values[75 * values.size() / 100 - 1] / 1000 << " "
			<< values[90 * values.size() / 100 - 1] / 1000 << endl;
	is.close();
}
int main() {

	for (int i = 0; i < 1; i++) {
		cout << "剪支前：" << i << endl;
		TreeNode* root = constructTree("c5_"+to_string(i*10)+".txt");
		cout << "完成决策树构建" << endl;
		int curDepth = 0, totalDepth = 0, totalLeafSize = 0;
		getLeafDepth(root, curDepth, totalDepth, totalLeafSize);
		cout << "总叶子深度： " << totalDepth << " 总叶子数： " << totalLeafSize
				<< " 平均叶子深度： " << totalDepth / totalLeafSize << endl;
		vector<vector<string>> result = levelOrder(root);
		int size = 0;
		for (auto a : result) {
			size += a.size();
		}
		cout << "最大树深度：" << result.size() << "总节点数： " << size << endl;
		string input =	
				".csv";
		string output =
				".csv";

		cout << "平均查找深度： " << doTest(input, root, output) << endl;
		//getBoxVal(output);
		 doPruning(root);
		 cout << "剪支后：" << i << endl;
		 curDepth = 0, totalDepth = 0, totalLeafSize = 0;
		 getLeafDepth(root, curDepth, totalDepth, totalLeafSize);
		 cout << "总叶子深度： " << totalDepth << " 总叶子数： "
		 << totalLeafSize << " 平均叶子深度： " << totalDepth / totalLeafSize
		 << endl;
		 result = levelOrder(root);
		 size = 0;
		 for (auto a : result) {
		 size += a.size();
		 }
		 cout << "最大树深度：" << result.size() << "总节点数： " << size
		 << endl;

		 output = ".csv";
		 cout << "平均查找深度： " << doTest(input, root, output) << endl;
		// getBoxVal(output);
		cout << "完成 " << endl << endl;
	}

