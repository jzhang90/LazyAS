A = load('C:\Users\layrong\Desktop\接入选择\数据\模糊\节点数.csv');
 
x = A(:,1:1);
y1 = A(:,2:2);
y2 = A(:,3:3); 
 
figure;
set(gcf,'unit','normalized','position',[0.2,0.2,0.25,0.25]);
 
plot(x,y1,'-bd','LineWidth',1);
hold on
plot(x,y2,':ro','LineWidth',1);

ylabel('#(Nodes)','FontSize',8,'Color','k');
xlabel('Δ (ms)','FontSize',8,'Color','k');
set(gca,'xcolor',[0,0,0])
set(gca,'ycolor',[0,0,0])
%%set(gca,'xscale','log')
%set(gca,'YLim',[10 40]);
%set(gca,'YTick',0:2:40);
le = legend('original ','pruned','Location','northeast');
%set(le,'Box','off');
set(gca,'FontSize',8)
