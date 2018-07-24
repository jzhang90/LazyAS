A = load('C:\Users\layrong\Desktop\接入选择\数据\模糊\模糊比-测试集.csv');
B = load('C:\Users\layrong\Desktop\接入选择\数据\模糊\模糊比-训练集.csv');
x = A(:,1:1);
y1 = A(:,2:2);
y2 = B(:,2:2); 

figure;
set(gcf,'unit','normalized','position',[0.2,0.2,0.25,0.25]);
 
plot(x,y2,'-bd','LineWidth',1);
hold on
plot(x,y1,':ro','LineWidth',1);

ylabel('Fuzzy Ratio','FontSize',8,'Color','k');
xlabel('Δ (ms)','FontSize',8,'Color','k');
set(gca,'xcolor',[0,0,0])
set(gca,'ycolor',[0,0,0])
%%set(gca,'xscale','log')
%set(gca,'XLim',[10 90]);
set(gca,'YTick',0:0.1:1);
le = legend('traing set','test set','Location','southeast');
%set(le,'Box','off');
set(gca,'FontSize',8)
