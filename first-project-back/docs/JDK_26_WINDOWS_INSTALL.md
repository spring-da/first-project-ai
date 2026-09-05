# Windows 安装 JDK 26 与配置环境变量

> 更新日期：2026-08-10

本后端项目使用 **Java 26 + Spring Boot 4.1.0**。

截至本文更新时间：

- JDK 26 是最新正式功能版本。
- JDK 25 是长期支持版本（LTS）。
- Spring Boot 4.1.0 官方支持 Java 17 到 Java 26。

本教程按照项目的 `pom.xml` 安装 JDK 26。如果以后更重视 LTS，可安装 JDK 25，并将 `pom.xml` 中的 `<java.version>` 改为 `25`。

官方资料：

- [Oracle Java 下载页面](https://www.oracle.com/java/technologies/downloads/)
- [Oracle JDK 26 Windows 安装文档](https://docs.oracle.com/en/java/javase/26/install/installation-jdk-microsoft-windows-platforms.html)
- [OpenJDK JDK 26 项目](https://openjdk.org/projects/jdk/26/)
- [Spring Boot 4.1 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)

## 一、检查当前环境

打开新的 PowerShell：

```powershell
java -version
javac -version
where.exe java
```

在生成本项目时，你的电脑检测结果仍然是：

```text
java version "1.8.0_202"
javac 1.8.0_202
```

这说明当前终端优先使用 Java 8，不能编译本项目。安装 JDK 26 后还需要调整 `JAVA_HOME` 和 `Path` 顺序。

## 二、下载 JDK 26

打开 Oracle Java 下载页面：

```text
https://www.oracle.com/java/technologies/downloads/
```

选择：

```text
Java 26 → Windows → x64 Installer
```

一般文件名类似：

```text
jdk-26_windows-x64_bin.exe
```

如果使用其他 OpenJDK 发行版，也可以选择 Eclipse Temurin、Microsoft Build of OpenJDK 或 Amazon Corretto，但 IDEA 与 Maven 必须指向同一个 JDK 26。

## 三、安装

1. 双击安装程序。
2. 使用管理员权限确认安装。
3. 建议保留默认目录。
4. 完成安装。

Oracle 默认安装目录通常是：

```text
C:\Program Files\Java\jdk-26
```

请进入该目录确认存在：

```text
bin\java.exe
bin\javac.exe
```

## 四、配置 JAVA_HOME

打开：

```text
开始菜单
→ 搜索“环境变量”
→ 编辑系统环境变量
→ 环境变量
```

在“系统变量”中新增或修改：

```text
变量名：JAVA_HOME
变量值：C:\Program Files\Java\jdk-26
```

注意：`JAVA_HOME` 指向 JDK 根目录，不要在末尾添加 `\bin`。

## 五、配置 Path

在“系统变量”中编辑 `Path`，新增：

```text
%JAVA_HOME%\bin
```

把它移动到旧 Java 路径前面。

特别检查并处理这些可能抢占优先级的路径：

```text
C:\Program Files\Common Files\Oracle\Java\javapath
旧的 jdk1.8...\bin
旧的 jre1.8...\bin
```

你不一定需要卸载 Java 8，但 `%JAVA_HOME%\bin` 必须排在这些旧路径之前。

## 六、重新打开终端并验证

环境变量修改后，完全关闭以下程序再重新打开：

- PowerShell / CMD
- VS Code
- IntelliJ IDEA

在新 PowerShell 中运行：

```powershell
java -version
javac -version
where.exe java
$env:JAVA_HOME
```

期望结果包含：

```text
java version "26"
javac 26
C:\Program Files\Java\jdk-26
```

如果仍然显示 `1.8`，说明旧 Java 路径仍排在 `%JAVA_HOME%\bin` 前面。

## 七、让 Maven 使用 JDK 26

你已经下载 Maven，但生成项目时终端还不能识别 `mvn`。如果执行：

```powershell
mvn -version
```

提示找不到命令，请配置：

```text
变量名：MAVEN_HOME
变量值：你的 Maven 解压目录，例如 E:\ProgramFiles\apache-maven-3.9.x
```

并在 `Path` 中新增：

```text
%MAVEN_HOME%\bin
```

重新打开 PowerShell，运行：

```powershell
mvn -version
```

重点确认输出中的 Java version 是 `26`，Java home 指向 JDK 26，而不是 Java 8。

## 八、配置 IntelliJ IDEA

打开后端项目后：

### Project SDK

```text
File
→ Project Structure
→ Project
→ SDK
→ Add SDK
→ JDK
→ C:\Program Files\Java\jdk-26
```

设置：

```text
Project SDK: JDK 26
Language level: SDK default / 26
```

### Maven JDK

```text
Settings
→ Build, Execution, Deployment
→ Build Tools
→ Maven
→ Runner
→ JRE: JDK 26
```

在 Maven Importer 中也选择 JDK 26，然后重新加载 Maven 项目。

## 九、验证后端项目

进入项目目录：

```powershell
cd E:\WorkProject\flutter\first-flutter-project-back
mvn -version
mvn clean test
```

数据库配置完成后启动：

```powershell
mvn spring-boot:run
```

访问：

```text
http://localhost:8080/actuator/health
```

## 常见问题

### `release version 26 not supported`

Maven 实际使用了旧 JDK。执行：

```powershell
mvn -version
```

修正 Maven JRE 或 `JAVA_HOME`。

### `java -version` 是 26，但 `mvn -version` 是 1.8

IDEA Maven Runner 或 Maven 启动脚本使用了单独的旧 JDK。统一改为 JDK 26。

### 修改环境变量后没有变化

旧终端不会自动刷新环境变量。完全关闭终端和 IDEA，再重新打开。

### 是否一定要安装 Oracle JDK

不是。兼容的 OpenJDK 26 发行版也可以。关键是：

```text
java、javac、Maven 和 IDEA 使用同一个 JDK 26
```
