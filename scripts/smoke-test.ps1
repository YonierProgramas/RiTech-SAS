[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Password = 'Ritech2026*'
)

$ErrorActionPreference = 'Stop'

$accounts = @{
    comercial     = 'comercial@ritech.example'
    ventas        = 'ventas@ritech.example'
    exportaciones = 'exportaciones@ritech.example'
    calidad       = 'calidad@ritech.example'
    auditoria     = 'auditoria@ritech.example'
}

function New-Session($email) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    try {
        Invoke-WebRequest -UseBasicParsing -WebSession $session -Uri "$BaseUrl/login" -Method Post `
            -Body @{ email = $email; password = $Password } | Out-Null
    }
    catch {
        throw "Login fallido para ${email}: $($_.Exception.Message)"
    }

    try {
        $sessionResponse = Invoke-WebRequest -UseBasicParsing -WebSession $session -Uri "$BaseUrl/api/session" -Method Get
        $finalPath = $sessionResponse.BaseResponse.ResponseUri.AbsolutePath
        if ([int]$sessionResponse.StatusCode -ne 200 -or $finalPath -ne '/api/session') {
            throw "La sesión no quedó autenticada"
        }
    }
    catch {
        throw "Login fallido para ${email}: $($_.Exception.Message)"
    }
    return $session
}

function Test-Endpoint($label, $email, $path) {
    try {
        $session = New-Session $email
        $response = Invoke-WebRequest -UseBasicParsing -WebSession $session -Uri "$BaseUrl$path" -Method Get
        if ([int]$response.StatusCode -ge 200 -and [int]$response.StatusCode -lt 300) {
            Write-Host "OK    $label [$($response.StatusCode)] $path" -ForegroundColor Green
        }
        else {
            Write-Host "ERROR $label [$($response.StatusCode)] $path" -ForegroundColor Red
        }
    }
    catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'N/A' }
        Write-Host "ERROR $label [$status] $path - $($_.Exception.Message)" -ForegroundColor Red
    }
}

Test-Endpoint 'dashboard' $accounts.comercial '/api/dashboard/operativo'
Test-Endpoint 'clientes' $accounts.comercial '/api/clientes'
Test-Endpoint 'productos' $accounts.ventas '/api/productos'
Test-Endpoint 'lotes' $accounts.ventas '/api/lotes'
Test-Endpoint 'pedidos' $accounts.ventas '/api/pedidos'
Test-Endpoint 'exportaciones' $accounts.exportaciones '/api/exportaciones'
Test-Endpoint 'calidad' $accounts.calidad '/api/calidad'
Test-Endpoint 'trazabilidad' $accounts.auditoria '/api/trazabilidad/DEMO-LOTE-001'
