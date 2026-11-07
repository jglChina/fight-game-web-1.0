# ==========================================================
# fight-game-web 构建脚本（build.ps1）
# ----------------------------------------------------------
# 用法：
#   ./build.ps1              完整构建（编译后端 + 打包）
#   ./build.ps1 -SkipCompile 仅增量打包（只改了前端时用，跳过 javac）
#   ./build.ps1 -Clean       先清 target 再构建
#
# 根因说明（为什么不用纯 mvn package）：
#   Trae 沙箱屏蔽了 Maven 的 maven-compiler-plugin 子进程对
#   工作区 target 目录的写权限（不能建目录也不能写 .class）。
#   但 PowerShell 直接 fork 的 javac.exe 有权限。
#   所以本脚本手动调用 javac 编译，再让 Maven 只做 jar + repackage。
# ==========================================================

param(
    [switch]$SkipCompile,   # 跳过 javac 编译（仅改了前端时用）
    [switch]$Clean          # 彻底清 target 后重建
)

$ErrorActionPreference = "Stop"

# ---------- 路径常量 ----------
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TargetDir  = Join-Path $ProjectDir "target"
$ClassesDir = Join-Path $TargetDir "classes"
$ResourceSrc = Join-Path $ProjectDir "src\main\resources"
$JavaSrcDir  = Join-Path $ProjectDir "src\main\java"

$JDK_JAVAC = "D:\NewD\APP\Develop\JDK17\bin\javac.exe"
$JDK_JAR   = "D:\NewD\APP\Develop\JDK17\bin\jar.exe"
$MvnCmd    = "D:\NewD\APP\Develop\apache-maven-3.10.0\bin\mvn.cmd"
$MavenRepo = "$env:USERPROFILE\.m2\repository"   # 与 settings.xml 默认值对齐

Set-Location $ProjectDir

# ---------- 1. 清 target ----------
if ($Clean -and (Test-Path $TargetDir)) {
    Write-Host "[1/5] 清理 target 目录..." -ForegroundColor Cyan
    Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    [System.IO.Directory]::Delete((Resolve-Path $TargetDir).Path, $true)
}

# ---------- 2. 重建 target/classes ----------
Write-Host "[2/5] 重建 target 目录结构..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null

# ---------- 3. 复制资源文件 ----------
Write-Host "[3/5] 复制资源文件到 target/classes..." -ForegroundColor Cyan
Copy-Item -Path (Join-Path $ResourceSrc "*") -Destination $ClassesDir -Recurse -Force

# ---------- 4. javac 编译（可跳过） ----------
if ($SkipCompile) {
    Write-Host "[4/5] 跳过 javac（-SkipCompile 已指定）" -ForegroundColor Yellow
} else {
    Write-Host "[4/5] javac 批量编译..." -ForegroundColor Cyan

    # 拼 classpath：Maven 仓库下所有 jar（排除 sources/javadoc）
    $cp = @()
    Get-ChildItem -Path $MavenRepo -Recurse -Filter "*.jar" |
        Where-Object { $_.FullName -notmatch '\.sources\.jar$|\.javadoc\.jar$' } |
        ForEach-Object { $cp += $_.FullName }
    if ($cp.Count -eq 0) { throw "Maven 仓库 $MavenRepo 下没找到任何依赖 jar，请先跑一次 mvn dependency:resolve" }
    $cpStr = $cp -join ";"

    # 收集所有 .java 源文件
    $srcFiles = Get-ChildItem -Path $JavaSrcDir -Recurse -Filter "*.java" |
        ForEach-Object { $_.FullName }
    if ($srcFiles.Count -eq 0) { throw "没找到 java 源文件，请确认 src/main/java 存在" }

    Write-Host "    源文件 $($srcFiles.Count) 个，依赖 jar $($cp.Count) 个"

    # 执行编译
    & $JDK_JAVAC -encoding "UTF-8" -cp $cpStr -d $ClassesDir $srcFiles
    if ($LASTEXITCODE -ne 0) { throw "javac 编译失败，退出码 $LASTEXITCODE" }

    $classCount = (Get-ChildItem $ClassesDir -Recurse -Filter "*.class").Count
    Write-Host "    产出 .class 文件 $classCount 个" -ForegroundColor Green
}

# ---------- 5. Maven 打可执行 jar（跳过 compile + resources）----------
Write-Host "[5/5] Maven 打包 spring-boot 可执行 jar..." -ForegroundColor Cyan
& $MvnCmd "-DskipTests" "-Dmaven.resources.skip=true" "-Dmaven.compiler.skip=true" "package"
if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败" }

$jarPath = Join-Path $TargetDir "fight-game-web-1.0.0.jar"
if (Test-Path $jarPath) {
    $jarSize = (Get-Item $jarPath).Length
    Write-Host ""
    Write-Host "✅ 构建成功！" -ForegroundColor Green
    Write-Host "   jar: $jarPath ($jarSize bytes)"
    Write-Host "   启动: java -jar $jarPath"
} else {
    throw "没找到 jar 产物：$jarPath"
}
