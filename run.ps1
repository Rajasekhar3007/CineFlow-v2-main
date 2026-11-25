$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ($scriptDir) { Set-Location $scriptDir }

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error 'Maven is not on PATH. Follow TEAM_SETUP.txt, then reopen PowerShell.'
    exit 1
}
if (-not (Test-Path -LiteralPath 'db.properties')) {
    Write-Error 'Create db.properties from db.properties.example first. See TEAM_SETUP.txt.'
    exit 1
}
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Current Directory: $(Get-Location)" -ForegroundColor Yellow
Write-Host "  Launching CineFlow - Multiplex Ticket System...       " -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Cyan
mvn compile javafx:run
exit $LASTEXITCODE
