1) 确认本地的 VERSION 与 pom.xml 与 本地branch 中的版本一致，不一致的话以pom.xml为准
2) mvn package 
3) 进入 ui-vue3 运行pnpm lint 
4) 退回项目目录， git merge upstream/main
5) 项目目录，运行 make ui-deploy
6) git 提交 branch到origin
7) git 打包 tag名字与pom的版本号一致 ，上传tag到 upstream (上传之前请先用git remote 看一下upstream是哪里，确认是spring-ai-alibaba/JManus)   