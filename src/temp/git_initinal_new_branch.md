1) 先查看当前branch
2) 切换回main 
3) merge fetch upstream  then merge upstream main 
4) push main to origin
5) 查看 pom.xml里面的最新项目版本号
6) 将版本号的小版本加1的数字，以这个做一个branch Vxxxxx
7) 切换到该branch , 同步更新pom.xml和 VERSION 与branch版本号一致
8) git push origin 