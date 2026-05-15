Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

$form = New-Object System.Windows.Forms.Form
$form.Text = 'GSMV API Keys'
$form.StartPosition = 'CenterScreen'
$form.Size = New-Object System.Drawing.Size(520, 300)
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.TopMost = $true

$title = New-Object System.Windows.Forms.Label
$title.Text = 'Enter AI API keys'
$title.Font = New-Object System.Drawing.Font('Segoe UI', 14, [System.Drawing.FontStyle]::Bold)
$title.AutoSize = $true
$title.Location = New-Object System.Drawing.Point(28, 22)
$form.Controls.Add($title)

$tip = New-Object System.Windows.Forms.Label
$tip.Text = 'You can leave both fields empty. Keys are used only for this launch.'
$tip.Font = New-Object System.Drawing.Font('Segoe UI', 9)
$tip.AutoSize = $true
$tip.Location = New-Object System.Drawing.Point(30, 58)
$form.Controls.Add($tip)

$bailianLabel = New-Object System.Windows.Forms.Label
$bailianLabel.Text = 'Bailian API Key'
$bailianLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9)
$bailianLabel.AutoSize = $true
$bailianLabel.Location = New-Object System.Drawing.Point(30, 95)
$form.Controls.Add($bailianLabel)

$bailianBox = New-Object System.Windows.Forms.TextBox
$bailianBox.Font = New-Object System.Drawing.Font('Consolas', 10)
$bailianBox.Location = New-Object System.Drawing.Point(30, 120)
$bailianBox.Size = New-Object System.Drawing.Size(440, 26)
$bailianBox.UseSystemPasswordChar = $true
$form.Controls.Add($bailianBox)

$deepseekLabel = New-Object System.Windows.Forms.Label
$deepseekLabel.Text = 'DeepSeek API Key'
$deepseekLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9)
$deepseekLabel.AutoSize = $true
$deepseekLabel.Location = New-Object System.Drawing.Point(30, 158)
$form.Controls.Add($deepseekLabel)

$deepseekBox = New-Object System.Windows.Forms.TextBox
$deepseekBox.Font = New-Object System.Drawing.Font('Consolas', 10)
$deepseekBox.Location = New-Object System.Drawing.Point(30, 183)
$deepseekBox.Size = New-Object System.Drawing.Size(440, 26)
$deepseekBox.UseSystemPasswordChar = $true
$form.Controls.Add($deepseekBox)

$okButton = New-Object System.Windows.Forms.Button
$okButton.Text = 'Start'
$okButton.Location = New-Object System.Drawing.Point(270, 225)
$okButton.Size = New-Object System.Drawing.Size(95, 30)
$okButton.DialogResult = [System.Windows.Forms.DialogResult]::OK
$form.Controls.Add($okButton)

$skipButton = New-Object System.Windows.Forms.Button
$skipButton.Text = 'Start Empty'
$skipButton.Location = New-Object System.Drawing.Point(375, 225)
$skipButton.Size = New-Object System.Drawing.Size(95, 30)
$skipButton.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$form.Controls.Add($skipButton)

$form.AcceptButton = $okButton
$form.CancelButton = $skipButton

[void]$form.ShowDialog()

$payload = @{
  bailian = $bailianBox.Text.Trim()
  deepseek = $deepseekBox.Text.Trim()
} | ConvertTo-Json -Compress

[Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($payload))
