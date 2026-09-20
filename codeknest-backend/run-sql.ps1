# ============================================
# SQL 执行脚本 — 调用 SqlRunner 执行任意 SQL
# 用法: .\run-sql.ps1 "SELECT * FROM t_user LIMIT 5"
#       .\run-sql.ps1 "DELETE FROM t_post_draft WHERE id=1"
# ============================================

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$webDir = Join-Path $root "codeknest-server-web"

if ($args.Count -eq 0) {
    Write-Host "用法: .\run-sql.ps1 `"SELECT * FROM t_user LIMIT 5`"" -ForegroundColor Yellow
    exit 1
}

$sql = $args -join " "

# 1. 编译 test 代码
Write-Host "[1/3] 编译..." -ForegroundColor Cyan
mvn -pl codeknest-server-web test-compile -q -DskipTests -o

# 2. 查找 mysql 驱动 jar（取最新版）
Write-Host "[2/3] 查找 mysql 驱动..." -ForegroundColor Cyan
$repo = "D:\develop\apache-maven-3.9.11-bin\apache-maven-3.9.11\mvn_repo\com\mysql\mysql-connector-j"
$mysqlJar = Get-ChildItem -Path $repo -Filter "mysql-connector-j-*.jar" -Recurse |
    Where-Object { $_.Name -notlike "*-sources.jar" } |
    Sort-Object Name -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $mysqlJar) {
    Write-Host "未找到 mysql 驱动 jar" -ForegroundColor Red
    exit 1
}
Write-Host "驱动: $mysqlJar" -ForegroundColor Gray

# 3. 执行 SQL
Write-Host "[3/3] 执行 SQL: $sql" -ForegroundColor Cyan
Write-Host ("-" * 60)
$cp = "$webDir\target\test-classes;$webDir\target\classes;$mysqlJar"
java -cp $cp com.codeknest.tools.SqlRunner $sql
