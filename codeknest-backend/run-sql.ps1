# ============================================
# SQL 执行脚本 — 调用 SqlRunner 执行任意 SQL
# 用法: .\run-sql.ps1 "SELECT * FROM t_user LIMIT 5"
#       .\run-sql.ps1 "DELETE FROM t_post_draft WHERE id=1"
#       .\run-sql.ps1 "DROP DATABASE IF EXISTS codeknest_bootstrap_test"
# 说明: 数据源配置读自 codeknest-common.yml（阶段 2 配置归一化后的位置），
#       凭据经根目录 .env 注入进程环境（命令行含明文密码会被安全策略拒绝）。
# ============================================

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$webDir = Join-Path $root "codeknest-server-web"

if ($args.Count -eq 0) {
    Write-Host "用法: .\run-sql.ps1 `"SELECT * FROM t_user LIMIT 5`"" -ForegroundColor Yellow
    exit 1
}

$sql = $args -join " "

# 0. 载入根目录 .env（真实凭据，不入库）
$envFile = Join-Path (Split-Path -Parent $root) ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+?)\s*$') {
            [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
    Write-Host "[0/3] 已载入 .env" -ForegroundColor Gray
} else {
    Write-Host "[0/3] 未找到 .env，将使用系统环境变量" -ForegroundColor Yellow
}

# 1. 编译 test 代码
Write-Host "[1/3] 编译..." -ForegroundColor Cyan
mvn -pl codeknest-server-web test-compile -q -DskipTests -o

# 2. 生成完整依赖 classpath（含 mysql 驱动与 codeknest-common，后者提供 codeknest-common.yml）
Write-Host "[2/3] 解析依赖 classpath..." -ForegroundColor Cyan
$cpFile = Join-Path $env:TEMP "codeknest-sql-classpath.txt"
mvn -pl codeknest-server-web -q -o dependency:build-classpath "-Dmdep.outputFile=$cpFile" -DincludeScope=test
if (-not (Test-Path $cpFile)) {
    Write-Host "未能生成依赖 classpath" -ForegroundColor Red
    exit 1
}
$depCp = (Get-Content $cpFile -Raw).Trim()

# 3. 执行 SQL
Write-Host "[3/3] 执行 SQL: $sql" -ForegroundColor Cyan
Write-Host ("-" * 60)
$cp = "$webDir\target\test-classes;$webDir\target\classes;$depCp"
java -cp $cp com.codeknest.tools.SqlRunner $sql
