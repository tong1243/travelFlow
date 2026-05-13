$ErrorActionPreference = 'Stop'

$root = 'D:\Java agent\demo-app\src\main\java\com\example\demo\rag'
$javaFiles = Get-ChildItem -Path $root -Recurse -File -Filter *.java

$modifierKeywords = @('public','protected','private','static','final','abstract','synchronized','native','default','strictfp','transient','volatile','sealed','non-sealed')
$controlKeywords = @('if','for','while','switch','catch','return','throw','new','do','try','else','case','super','this','assert')

function Get-ModuleType([string]$path) {
    $relative = $path.Substring($root.Length).TrimStart('\\')
    $parts = $relative.Split('\\')
    if ($parts.Length -ge 2) { return $parts[0].ToLowerInvariant() }
    return 'root'
}

function Get-LeadingIndent([string]$line) {
    $m = [regex]::Match($line, '^\s*')
    return $m.Value
}

function Has-JavadocBefore($lines, [int]$index) {
    $j = $index - 1
    while ($j -ge 0) {
        $t = $lines[$j].Trim()
        if ($t -eq '') { $j--; continue }
        if ($t.StartsWith('@')) { $j--; continue }
        break
    }
    if ($j -lt 0) { return $false }

    $t = $lines[$j].Trim()
    if ($t.StartsWith('/**')) { return $true }
    if (-not $t.EndsWith('*/')) { return $false }

    $k = $j
    while ($k -ge 0) {
        $x = $lines[$k].Trim()
        if ($x.StartsWith('/**')) { return $true }
        if ($x.StartsWith('/*') -and -not $x.StartsWith('/**')) { return $false }
        $k--
    }
    return $false
}

function Get-DeclarationBlock($lines, [int]$start) {
    $end = $start
    while ($end -lt $lines.Count) {
        $text = $lines[$end]
        if ($text -match '[\{;]\s*$' -or $text.Contains('{') -or $text.Trim().EndsWith(';')) {
            break
        }
        $end++
    }
    if ($end -ge $lines.Count) { $end = $lines.Count - 1 }
    $slice = $lines[$start..$end]
    $joined = ($slice -join ' ').Trim()
    return [PSCustomObject]@{ End = $end; Text = $joined }
}

function Split-Params([string]$paramText) {
    $result = New-Object System.Collections.Generic.List[string]
    if ([string]::IsNullOrWhiteSpace($paramText)) { return $result }

    $current = ''
    $angle = 0
    $paren = 0
    $square = 0
    for ($i = 0; $i -lt $paramText.Length; $i++) {
        $ch = $paramText[$i]
        switch ($ch) {
            '<' { $angle++; $current += $ch; continue }
            '>' { if ($angle -gt 0) { $angle-- }; $current += $ch; continue }
            '(' { $paren++; $current += $ch; continue }
            ')' { if ($paren -gt 0) { $paren-- }; $current += $ch; continue }
            '[' { $square++; $current += $ch; continue }
            ']' { if ($square -gt 0) { $square-- }; $current += $ch; continue }
            ',' {
                if ($angle -eq 0 -and $paren -eq 0 -and $square -eq 0) {
                    $part = $current.Trim()
                    if ($part) { $result.Add($part) }
                    $current = ''
                    continue
                }
                $current += $ch
                continue
            }
            default { $current += $ch }
        }
    }
    $tail = $current.Trim()
    if ($tail) { $result.Add($tail) }
    return $result
}

function Normalize-Signature([string]$text) {
    return ([regex]::Replace($text, '\s+', ' ')).Trim()
}

function Parse-Params([string]$signature) {
    $open = $signature.IndexOf('(')
    if ($open -lt 0) { return @() }
    $depth = 0
    $close = -1
    for ($i = $open; $i -lt $signature.Length; $i++) {
        $c = $signature[$i]
        if ($c -eq '(') { $depth++ }
        elseif ($c -eq ')') {
            $depth--
            if ($depth -eq 0) { $close = $i; break }
        }
    }
    if ($close -lt 0) { return @() }
    $paramText = $signature.Substring($open + 1, $close - $open - 1).Trim()
    if (-not $paramText) { return @() }

    $items = Split-Params $paramText
    $names = New-Object System.Collections.Generic.List[string]
    foreach ($item in $items) {
        $clean = [regex]::Replace($item, '@[\w$.]+(\([^)]*\))?\s*', '')
        $clean = $clean.Trim()
        if (-not $clean) { continue }
        $tokens = @([regex]::Split($clean, '\s+') | Where-Object { $_ -ne '' })
        if ($tokens.Count -eq 0) { continue }
        $name = [string]$tokens[$tokens.Count - 1]
        $name = $name -replace '\[\]$', ''
        $name = $name -replace '^\.{3}', ''
        $name = $name -replace '\.{3}', ''
        if ($name -match '^[A-Za-z_][A-Za-z0-9_]*$') {
            $names.Add($name)
        }
    }
    return $names
}

function Try-ParseClassDecl([string]$signature) {
    $sig = Normalize-Signature $signature
    $m = [regex]::Match($sig, '\b(class|interface|record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)')
    if (-not $m.Success) { return $null }

    $declType = $m.Groups[1].Value
    $name = $m.Groups[2].Value
    $recordParams = @()
    if ($declType -eq 'record') {
        $startIdx = $sig.IndexOf($name) + $name.Length
        $open = $sig.IndexOf('(', $startIdx)
        if ($open -ge 0) {
            $depth = 0
            $close = -1
            for ($i = $open; $i -lt $sig.Length; $i++) {
                $c = $sig[$i]
                if ($c -eq '(') { $depth++ }
                elseif ($c -eq ')') {
                    $depth--
                    if ($depth -eq 0) { $close = $i; break }
                }
            }
            if ($close -gt $open) {
                $pt = $sig.Substring($open + 1, $close - $open - 1).Trim()
                if ($pt) {
                    $items = Split-Params $pt
                    foreach ($item in $items) {
                        $tokens = @([regex]::Split($item.Trim(), '\s+') | Where-Object { $_ -ne '' })
                        if ($tokens.Count -gt 0) {
                            $pn = [string]$tokens[$tokens.Count - 1]
                            $pn = $pn -replace '\[\]$', ''
                            if ($pn -match '^[A-Za-z_][A-Za-z0-9_]*$') {
                                $recordParams += $pn
                            }
                        }
                    }
                }
            }
        }
    }

    return [PSCustomObject]@{
        Type = $declType
        Name = $name
        RecordParams = $recordParams
    }
}

function Try-ParseMethodDecl([string]$signature) {
    $sig = Normalize-Signature $signature
    if (-not $sig.Contains('(')) { return $null }
    if ($sig -match '\b(class|interface|record|enum)\b') { return $null }

    $prefix = $sig.Substring(0, $sig.IndexOf('(')).Trim()
    if (-not $prefix) { return $null }

    $first = ([string](($prefix -split '\s+')[0])).ToLowerInvariant()
    if ($controlKeywords -contains $first) { return $null }

    $tokens = @([regex]::Split($prefix, '\s+') | Where-Object { $_ -ne '' })
    if ($tokens.Count -lt 1) { return $null }

    $name = [string]$tokens[$tokens.Count - 1]
    if ($name.Contains('.')) { return $null }
    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { return $null }

    $prefixTokens = @()
    if ($tokens.Count -gt 1) {
        $prefixTokens = @($tokens[0..($tokens.Count - 2)])
    }

    $filtered = New-Object System.Collections.Generic.List[string]
    foreach ($t in $prefixTokens) {
        $tt = [string]$t
        if (-not $tt) { continue }
        if ($modifierKeywords -contains $tt) { continue }
        if ($tt.StartsWith('@')) { continue }
        if ($tt -match '^<.*>$') { continue }
        $filtered.Add($tt)
    }

    $isConstructor = $filtered.Count -eq 0
    $returnType = if ($isConstructor) { '' } else { $filtered[$filtered.Count - 1] }

    $params = Parse-Params $sig
    return [PSCustomObject]@{
        Name = $name
        Params = $params
        ReturnType = $returnType
        IsConstructor = $isConstructor
    }
}

function Build-ClassComment([string]$moduleType, [string]$declType, [string]$name, $recordParams) {
    $lines = New-Object System.Collections.Generic.List[string]
    $kindText = switch ($declType) {
        'interface' { '接口' }
        'record' { '记录类型' }
        'enum' { '枚举类型' }
        default { '类' }
    }

    $roleText = switch ($moduleType) {
        'controller' { '负责承接接口请求并组织应用层调用流程' }
        'service' { '负责组织核心业务逻辑并协调上下游组件' }
        'config' { '负责提供模块配置项与基础设施装配能力' }
        'entity' { '负责描述持久化实体结构并维护生命周期行为' }
        'repo' { '负责声明数据访问语义并交由框架生成实现' }
        'security' { '负责认证授权与访问安全相关逻辑' }
        'filter' { '负责请求过滤链路中的前后置处理' }
        'dto' { '负责模块边界上的请求/响应数据封装' }
        'model' { '负责承载检索与计算过程中的中间模型数据' }
        'langchain' { '负责大模型提示词与上下文组装' }
        default { '负责 RAG 模块内的基础支撑能力' }
    }

    $lines.Add('/**')
    $lines.Add(" * $name$kindText。")
    $lines.Add(" * 该类型$roleText，作为 rag 包内可维护、可扩展的一部分。")
    $lines.Add(' * 统一补充中文详细注释，便于联调排障、问题定位和后续功能迭代。')
    if ($declType -eq 'record' -and $recordParams.Count -gt 0) {
        foreach ($p in $recordParams) {
            $lines.Add(" * @param $p 记录字段`$p`，用于在调用边界中传递同名业务数据。")
        }
    }
    $lines.Add(' */')
    return $lines
}

function Build-MethodComment([string]$moduleType, $method) {
    $lines = New-Object System.Collections.Generic.List[string]
    $name = $method.Name

    $detailLine = switch ($moduleType) {
        'controller' { '该方法位于控制层，负责处理入参、承接用户上下文并返回统一响应。' }
        'service' { '该方法位于服务层，会组织业务步骤并调用仓储、向量检索或外部模型能力。' }
        'config' { '该方法用于配置注入与运行参数管理，直接影响模块初始化和运行行为。' }
        'entity' { '该方法用于维护实体状态或生命周期过程，确保持久化数据在读写时一致。' }
        'repo' { '该方法用于描述仓储查询语义，通常由 Spring Data 在运行时生成具体实现。' }
        'security' { '该方法用于认证授权链路处理，保障接口访问安全和身份可信。' }
        'filter' { '该方法运行在过滤链路中，用于请求前置校验或响应后置处理。' }
        'dto' { '该方法用于数据对象的访问或转换，保持调用契约清晰稳定。' }
        'model' { '该方法用于模型字段计算与读取，服务于检索融合流程。' }
        'langchain' { '该方法用于提示词与上下文构建，影响大模型输出质量与可解释性。' }
        default { '该方法承担当前组件中的具体处理步骤，并遵循模块统一约定。' }
    }

    $lower = $name.ToLowerInvariant()
    if ($method.IsConstructor) {
        $lines.Add('/**')
        $lines.Add(" * 构造并初始化 $name 所在对象。")
        $lines.Add(' * 该构造方法会注入运行所需依赖，保证对象在创建后即可参与后续业务流程。')
        $lines.Add($detailLine)
    } elseif ($lower.StartsWith('get')) {
        $field = if ($name.Length -gt 3) { $name.Substring(3) } else { '目标字段' }
        $lines.Add('/**')
        $lines.Add(" * 获取 $field 的当前值。")
        $lines.Add(' * 该方法用于对外暴露只读访问能力，避免调用方直接操作内部字段。')
        $lines.Add($detailLine)
    } elseif ($lower.StartsWith('set')) {
        $field = if ($name.Length -gt 3) { $name.Substring(3) } else { '目标字段' }
        $lines.Add('/**')
        $lines.Add(" * 设置 $field 的值。")
        $lines.Add(' * 该方法用于集中维护字段写入入口，便于后续扩展校验和状态同步逻辑。')
        $lines.Add($detailLine)
    } elseif ($lower.StartsWith('is')) {
        $field = if ($name.Length -gt 2) { $name.Substring(2) } else { '条件' }
        $lines.Add('/**')
        $lines.Add(" * 判断 $field 是否满足预期。")
        $lines.Add(' * 该方法返回布尔判定结果，供上层流程进行分支控制或策略选择。')
        $lines.Add($detailLine)
    } else {
        $lines.Add('/**')
        $lines.Add(" * 执行 $name 相关处理逻辑。")
        $lines.Add(' * 该方法会根据输入参数完成一次完整步骤，并按约定返回处理结果。')
        $lines.Add($detailLine)
    }

    foreach ($p in $method.Params) {
        $lines.Add(" * @param $p 输入参数`$p`，用于参与本次业务处理或流程控制。")
    }

    if (-not $method.IsConstructor -and $method.ReturnType -and $method.ReturnType -ne 'void') {
        if ($method.ReturnType -eq 'boolean' -or $method.ReturnType -eq 'Boolean') {
            $lines.Add(' * @return 返回布尔判断结果，`true` 表示满足条件，`false` 表示不满足条件。')
        } else {
            $lines.Add(' * @return 返回该步骤的处理结果；若无有效结果，按实现约定返回空值或默认值。')
        }
    }

    $lines.Add(' */')
    return $lines
}

$changed = 0

foreach ($file in $javaFiles) {
    $lines = Get-Content -Path $file.FullName -Encoding UTF8
    if ($lines.Count -eq 0) { continue }

    $moduleType = Get-ModuleType $file.FullName
    $newLines = New-Object System.Collections.Generic.List[string]

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $trim = $line.Trim()

        if ($trim -eq '') {
            $newLines.Add($line)
            continue
        }

        $declBlock = Get-DeclarationBlock $lines $i
        $signature = $declBlock.Text

        $classDecl = Try-ParseClassDecl $signature
        if ($classDecl -ne $null -and $trim -notmatch '^package\b' -and $trim -notmatch '^import\b') {
            if (-not (Has-JavadocBefore $lines $i)) {
                $indent = Get-LeadingIndent $line
                $commentLines = Build-ClassComment $moduleType $classDecl.Type $classDecl.Name $classDecl.RecordParams
                foreach ($cl in $commentLines) {
                    $newLines.Add($indent + $cl)
                }
            }
            $newLines.Add($line)
            continue
        }

        $looksLikeCandidate = $trim.Contains('(') -and -not $trim.StartsWith('@') -and -not $trim.StartsWith('//') -and -not $trim.StartsWith('*') -and -not $trim.StartsWith('/*') -and -not $trim.StartsWith('package ') -and -not $trim.StartsWith('import ')
        if ($looksLikeCandidate) {
            $methodDecl = Try-ParseMethodDecl $signature
            if ($methodDecl -ne $null) {
                if (-not (Has-JavadocBefore $lines $i)) {
                    $indent = Get-LeadingIndent $line
                    $commentLines = Build-MethodComment $moduleType $methodDecl
                    foreach ($cl in $commentLines) {
                        $newLines.Add($indent + $cl)
                    }
                }
                $newLines.Add($line)
                continue
            }
        }

        $newLines.Add($line)
    }

    $oldText = ($lines -join "`n")
    $newText = ($newLines -join "`n")
    if ($oldText -ne $newText) {
        Set-Content -Path $file.FullName -Value $newText -Encoding UTF8
        $changed++
    }
}

Write-Output "UPDATED_FILES=$changed"
