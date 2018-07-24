A = load('C:\Users\layrong\Desktop\接入选择\数据\模糊\剪支前后.csv');%total5-bin.csv
X1 = A(1:1,2:6);
 
y1 = A(1:1,7:11);
X2 = A(2:2,2:6);
y2 = A(2:2,7:11);
X3 = A(3:3,2:6);
y3 = A(3:3,7:11);
X4 = A(4:4,2:6);
y4 = A(4:4,7:11);
X5 = A(5:5,2:6);
y5 = A(5:5,7:11);
X6 = A(6:6,2:6);
y6 = A(6:6,7:11);
X7 = A(7:7,2:6);
y7 = A(7:7,7:11);
X8 = A(8:8,2:6);
y8 = A(8:8,7:11);
X9 = A(9:9,2:6);
y9 = A(9:9,7:11);
X10 = A(10:10,2:6);
y10 = A(10:10,7:11);
 X11 = A(11:11,2:6);
y11 = A(11:11,7:11);
%set(gca,'YTick',[0:0.1:1]) %设置x轴所代表大时
% boxplot([X1;X2;X3;X4;X5;X6;X7;X8;X9;X10].','positions',[2,4,6,8,10,12,14,16,18,20],'colors','b','width',0.3);
% hold on;
% boxplot([y1;y2;y3;y4;y5;y6;y7;y8;y9;y10].','positions',[3,5,7,9,11,13,15,17,19,21],'colors','r','width',0.5);

boxplot([X1;X2;X3;X4;X5;X6;X7;X8;X9;X10;X11].','positions',[2,5,8,11,14,17,20,23,26,29,32],'colors','b','width',0.7);
hold on;
boxplot([y1;y2;y3;y4;y5;y6;y7;y8;y9;y10;y11].','positions',[3,6,9,12,15,18,21,24,27,30,33],'colors','r','width',0.7);
set(gcf,'unit','normalized','position',[0.2,0.2,0.25,0.25]);
set(gca,'xticklabel',{'0' '10' '20' '30' '40' '50' '60' '70' '80' '90' '100'})
ylabel('Wireless Delay (ms)','FontSize',8,'Color','k');
xlabel('Δ (ms)','FontSize',8,'Color','k');
%title('Wireless Delay-Δ(ms) for Original and Pruned');
set(gca,'YLim',[0 70]);
set(gca,'xLim',[1 35]);
set(gca,'xcolor',[0,0,0])
set(gca,'ycolor',[0,0,0])
set(gca,'FontSize',8);
a=plot(0:0,'b');
b=plot(0:0,'r'); 
legend('original','pruned','Location','northwest');
 