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
    ([regex]::Match($line, '^\s*')).Value
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

    for ($k = $j; $k -ge 0; $k--) {
        $x = $lines[$k].Trim()
        if ($x.StartsWith('/**')) { return $true }
        if ($x.StartsWith('/*') -and -not $x.StartsWith('/**')) { return $false }
    }
    return $false
}

function Normalize-Signature([string]$text) {
    ([regex]::Replace($text, '\s+', ' ')).Trim()
}

function Collect-Signature($lines, [int]$start) {
    $end = $start
    $paren = 0
    $foundParen = $false
    while ($end -lt $lines.Count) {
        $line = $lines[$end]
        foreach ($ch in $line.ToCharArray()) {
            if ($ch -eq '(') { $paren++; $foundParen = $true }
            elseif ($ch -eq ')') { if ($paren -gt 0) { $paren-- } }
        }

        $trim = $line.Trim()
        if (($trim.EndsWith('{') -or $trim.EndsWith(';') -or $line.Contains('{') -or $line.Contains(';')) -and (-not $foundParen -or $paren -le 0)) {
            break
        }
        $end++
    }
    if ($end -ge $lines.Count) { $end = $lines.Count - 1 }
    $text = ($lines[$start..$end] -join ' ')
    return [PSCustomObject]@{ End = $end; Text = (Normalize-Signature $text) }
}

function Split-Params([string]$paramText) {
    $result = New-Object System.Collections.Generic.List[string]
    if ([string]::IsNullOrWhiteSpace($paramText)) { return $result }

    $current = ''
    $angle = 0
    $paren = 0
    $square = 0
    foreach ($ch in $paramText.ToCharArray()) {
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
    $items = Split-Params $paramText
    $names = New-Object System.Collections.Generic.List[string]

    foreach ($item in $items) {
        $clean = [regex]::Replace($item, '@[\w$.]+(\([^)]*\))?\s*', '')
        $tokens = @([regex]::Split($clean.Trim(), '\s+') | Where-Object { $_ -ne '' })
        if ($tokens.Count -eq 0) { continue }
        $name = [string]$tokens[$tokens.Count - 1]
        $name = $name -replace '\.{3}', ''
        $name = $name -replace '\[\]$', ''
        if ($name -match '^[A-Za-z_][A-Za-z0-9_]*$') {
            $names.Add($name)
        }
    }
    return $names
}

function Try-ParseClassDecl([string]$signature) {
    $m = [regex]::Match($signature, '^(?:public|protected|private|abstract|final|sealed|non-sealed|static|\s)*\b(class|interface|record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)')
    if (-not $m.Success) { return $null }
    $declType = $m.Groups[1].Value
    $name = $m.Groups[2].Value

    $recordParams = @()
    if ($declType -eq 'record') {
        $open = $signature.IndexOf('(', $m.Index)
        if ($open -ge 0) {
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
            if ($close -gt $open) {
                $paramText = $signature.Substring($open + 1, $close - $open - 1)
                foreach ($item in (Split-Params $paramText)) {
                    $tokens = @([regex]::Split($item.Trim(), '\s+') | Where-Object { $_ -ne '' })
                    if ($tokens.Count -gt 0) {
                        $pn = [string]$tokens[$tokens.Count - 1]
                        $pn = $pn -replace '\[\]$', ''
                        if ($pn -match '^[A-Za-z_][A-Za-z0-9_]*$') { $recordParams += $pn }
                    }
                }
            }
        }
    }

    return [PSCustomObject]@{ Type = $declType; Name = $name; RecordParams = $recordParams }
}

function Try-ParseMethodDecl([string]$signature, $classNames) {
    if (-not $signature.Contains('(')) { return $null }
    if ($signature -match '\b(class|interface|record|enum)\b') { return $null }

    $open = $signature.IndexOf('(')
    $before = $signature.Substring(0, $open).Trim()
    if (-not $before) { return $null }

    $first = ([string](($before -split '\s+')[0])).ToLowerInvariant()
    if ($controlKeywords -contains $first) { return $null }

    if ($before.Contains('=')) {
        $eqIdx = $before.IndexOf('=')
        if ($eqIdx -ge 0) { return $null }
    }

    $tokens = @([regex]::Split($before, '\s+') | Where-Object { $_ -ne '' })
    if ($tokens.Count -lt 1) { return $null }

    $name = [string]$tokens[$tokens.Count - 1]
    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { return $null }

    $prefixTokens = @()
    if ($tokens.Count -gt 1) { $prefixTokens = @($tokens[0..($tokens.Count - 2)]) }

    $filtered = New-Object System.Collections.Generic.List[string]
    foreach ($t in $prefixTokens) {
        $tt = [string]$t
        if (-not $tt) { continue }
        if ($modifierKeywords -contains $tt) { continue }
        if ($tt.StartsWith('@')) { continue }
        if ($tt -match '^<.*>$') { continue }
        $filtered.Add($tt)
    }

    $isConstructor = $false
    if ($filtered.Count -eq 0) {
        $isConstructor = $true
    } elseif ($classNames -and ($classNames -contains $name) -and $filtered.Count -eq 1 -and ($modifierKeywords -contains $filtered[0])) {
        $isConstructor = $true
    }

    $returnType = if ($isConstructor) { '' } else { if ($filtered.Count -gt 0) { $filtered[$filtered.Count - 1] } else { '' } }

    $params = Parse-Params $signature
    return [PSCustomObject]@{ Name = $name; Params = $params; ReturnType = $returnType; IsConstructor = $isConstructor }
}

function Build-ClassComment([string]$moduleType, [string]$declType, [string]$name, $recordParams) {
    $lines = New-Object System.Collections.Generic.List[string]
    $kind = switch ($declType) { 'interface' { '接口' } 'record' { '记录类型' } 'enum' { '枚举类型' } default { '类' } }
    $role = switch ($moduleType) {
        'controller' { '负责接收并处理接口请求，协调服务层完成业务响应。' }
        'service' { '负责组织核心业务流程，串联检索、存储与模型调用能力。' }
        'config' { '负责定义模块配置项和基础 Bean 装配，影响运行时行为。' }
        'entity' { '负责描述持久化结构，并承载实体生命周期相关逻辑。' }
        'repo' { '负责声明数据访问能力，由 Spring Data 生成具体实现。' }
        'security' { '负责认证授权与访问控制，保障系统安全边界。' }
        'filter' { '负责请求链路的前后置处理，统一处理审计与限流等横切逻辑。' }
        'dto' { '负责封装请求与响应数据，保证接口契约清晰稳定。' }
        'model' { '负责承载检索阶段的中间结果，便于融合排序与结果转换。' }
        'langchain' { '负责组装提示词与上下文内容，提升模型输出的稳定性与可解释性。' }
        default { '负责 RAG 模块中的基础支撑逻辑。' }
    }
    $lines.Add('/**')
    $lines.Add(" * $name$kind。")
    $lines.Add(" * 该类型$role")
    $lines.Add(' * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。')
    if ($declType -eq 'record' -and $recordParams.Count -gt 0) {
        foreach ($p in $recordParams) {
            $lines.Add(" * @param ${p} 记录字段 `${p}`，用于传递该对象的业务数据。")
        }
    }
    $lines.Add(' */')
    return $lines
}

function Build-MethodComment([string]$moduleType, $method) {
    $lines = New-Object System.Collections.Generic.List[string]
    $name = $method.Name
    $lower = $name.ToLowerInvariant()

    $context = switch ($moduleType) {
        'controller' { '该方法位于控制层，负责参数承接、上下文透传和响应封装。' }
        'service' { '该方法位于服务层，负责组织业务步骤并协调上下游依赖。' }
        'config' { '该方法用于配置管理与 Bean 装配，直接影响模块运行效果。' }
        'entity' { '该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。' }
        'repo' { '该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。' }
        'security' { '该方法用于认证授权处理，确保访问链路符合安全策略。' }
        'filter' { '该方法运行在过滤链上，用于处理请求前置校验和响应后置动作。' }
        default { '该方法遵循当前模块约定，承担明确的输入处理与结果输出职责。' }
    }

    $lines.Add('/**')
    if ($method.IsConstructor) {
        $lines.Add(" * 构造并初始化 ${name} 对象。")
        $lines.Add(' * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。')
    } elseif ($lower.StartsWith('get')) {
        $field = if ($name.Length -gt 3) { $name.Substring(3) } else { '目标字段' }
        $lines.Add(" * 获取 ${field} 字段值。")
        $lines.Add(' * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。')
    } elseif ($lower.StartsWith('set')) {
        $field = if ($name.Length -gt 3) { $name.Substring(3) } else { '目标字段' }
        $lines.Add(" * 设置 ${field} 字段值。")
        $lines.Add(' * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。')
    } elseif ($lower.StartsWith('is') -or $lower.StartsWith('has')) {
        $lines.Add(" * 执行 ${name} 条件判断。")
        $lines.Add(' * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。')
    } else {
        $lines.Add(" * 执行 ${name} 业务处理。")
        $lines.Add(' * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。')
    }
    $lines.Add(" * ${context}")

    foreach ($p in $method.Params) {
        $lines.Add(" * @param ${p} 输入参数 `${p}`，用于参与本次处理流程。")
    }

    if (-not $method.IsConstructor -and $method.ReturnType -and $method.ReturnType -ne 'void') {
        if ($method.ReturnType -eq 'boolean' -or $method.ReturnType -eq 'Boolean') {
            $lines.Add(' * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。')
        } else {
            $lines.Add(' * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。')
        }
    }

    $lines.Add(' */')
    return $lines
}

function Count-BraceDelta([string]$line) {
    $open = ([regex]::Matches($line, '\{')).Count
    $close = ([regex]::Matches($line, '\}')).Count
    return $open - $close
}

$changed = 0

foreach ($file in $javaFiles) {
    $lines = Get-Content -Path $file.FullName -Encoding UTF8
    if ($lines.Count -eq 0) { continue }

    $moduleType = Get-ModuleType $file.FullName
    $newLines = New-Object System.Collections.Generic.List[string]

    $braceDepth = 0
    $classStack = New-Object System.Collections.Generic.List[object]

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $trim = $line.Trim()

        $currentClassDepth = if ($classStack.Count -gt 0) { $classStack[$classStack.Count - 1].BodyDepth } else { $null }
        $atMemberDepth = ($currentClassDepth -ne $null -and $braceDepth -eq $currentClassDepth)

        $addedComment = $false

        # class/interface/record/enum declaration (only when current line itself contains class keyword)
        if ($trim -match '^(?:public|protected|private|abstract|final|sealed|non-sealed|static|\s)*\b(class|interface|record|enum)\b') {
            $decl = Collect-Signature $lines $i
            $classInfo = Try-ParseClassDecl $decl.Text
            if ($classInfo -ne $null -and -not (Has-JavadocBefore $lines $i)) {
                $indent = Get-LeadingIndent $line
                foreach ($cl in (Build-ClassComment $moduleType $classInfo.Type $classInfo.Name $classInfo.RecordParams)) {
                    $newLines.Add($indent + $cl)
                }
                $addedComment = $true
            }
        }

        if (-not $addedComment -and $atMemberDepth) {
            $candidate = $trim
            if ($candidate -and -not $candidate.StartsWith('@') -and -not $candidate.StartsWith('//') -and -not $candidate.StartsWith('*') -and -not $candidate.StartsWith('/*')) {
                if ($candidate.Contains('(') -and -not $candidate.StartsWith('if ') -and -not $candidate.StartsWith('for ') -and -not $candidate.StartsWith('while ') -and -not $candidate.StartsWith('switch ') -and -not $candidate.StartsWith('catch ') -and -not $candidate.StartsWith('return ') -and -not $candidate.StartsWith('throw ') -and -not $candidate.StartsWith('new ')) {
                    $beforeParen = $candidate.Substring(0, $candidate.IndexOf('('))
                    if (-not $beforeParen.Contains('=')) {
                        $decl = Collect-Signature $lines $i
                        $classNames = @($classStack | ForEach-Object { $_.Name })
                        $method = Try-ParseMethodDecl $decl.Text $classNames
                        if ($method -ne $null -and -not (Has-JavadocBefore $lines $i)) {
                            $indent = Get-LeadingIndent $line
                            foreach ($ml in (Build-MethodComment $moduleType $method)) {
                                $newLines.Add($indent + $ml)
                            }
                        }
                    }
                }
            }
        }

        $newLines.Add($line)

        # update brace depth and class stack by current source line
        $delta = Count-BraceDelta $line

        # push class after seeing declaration line with opening brace
        if ($trim -match '^(?:public|protected|private|abstract|final|sealed|non-sealed|static|\s)*\b(class|interface|record|enum)\b') {
            $classInfo = Try-ParseClassDecl (Normalize-Signature $trim)
            if ($classInfo -ne $null -and $line.Contains('{')) {
                $classStack.Add([PSCustomObject]@{ Name = $classInfo.Name; BodyDepth = $braceDepth + 1 })
            }
        }

        $braceDepth += $delta

        while ($classStack.Count -gt 0 -and $braceDepth -lt $classStack[$classStack.Count - 1].BodyDepth) {
            $classStack.RemoveAt($classStack.Count - 1)
        }
    }

    $oldText = ($lines -join "`n")
    $newText = ($newLines -join "`n")
    if ($oldText -ne $newText) {
        Set-Content -Path $file.FullName -Value $newText -Encoding UTF8
        $changed++
    }
}

Write-Output "UPDATED_FILES=$changed"
