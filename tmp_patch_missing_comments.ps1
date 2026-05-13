$ErrorActionPreference='Stop'
$root='D:\Java agent\demo-app\src\main\java\com\example\demo\rag'
$files=Get-ChildItem -Path $root -Recurse -File -Filter *.java
$modifierKeywords=@('public','protected','private','static','final','abstract','synchronized','native','default','strictfp','transient','volatile','sealed','non-sealed')
$controlKeywords=@('if','for','while','switch','catch','return','throw','new','do','try','else','case','super','this','assert')

function HasDoc($lines,[int]$i){
  $j=$i-1
  while($j -ge 0){
    $t=$lines[$j].Trim(); if($t -eq ''){$j--;continue}; if($t.StartsWith('@')){$j--;continue}; break
  }
  if($j -lt 0){return $false}
  $t=$lines[$j].Trim();
  if($t.StartsWith('/**')){return $true}
  if(-not $t.EndsWith('*/')){return $false}
  for($k=$j;$k -ge 0;$k--){
    $x=$lines[$k].Trim();
    if($x.StartsWith('/**')){return $true}
    if($x.StartsWith('/*') -and -not $x.StartsWith('/**')){return $false}
  }
  return $false
}

function GetModule([string]$path){
  $rel=$path.Substring($root.Length).TrimStart('\\');
  $parts=$rel.Split('\\');
  if($parts.Length -ge 2){return $parts[0].ToLowerInvariant()}
  return 'root'
}

function Normalize([string]$s){([regex]::Replace($s,'\s+',' ')).Trim()}

function CollectSig($lines,[int]$start){
  $end=$start; $paren=0; $found=$false
  while($end -lt $lines.Count){
    $line=$lines[$end]
    foreach($ch in $line.ToCharArray()){ if($ch -eq '('){$paren++;$found=$true}elseif($ch -eq ')'){if($paren -gt 0){$paren--}} }
    $trim=$line.Trim()
    if(($trim.EndsWith('{') -or $trim.EndsWith(';') -or $line.Contains('{') -or $line.Contains(';')) -and (-not $found -or $paren -le 0)){break}
    $end++
  }
  if($end -ge $lines.Count){$end=$lines.Count-1}
  [PSCustomObject]@{End=$end;Text=(Normalize (($lines[$start..$end]-join ' ')))}
}

function ParseParams([string]$sig){
  $open=$sig.IndexOf('('); if($open -lt 0){return @()}
  $depth=0;$close=-1
  for($i=$open;$i -lt $sig.Length;$i++){
    $c=$sig[$i]; if($c -eq '('){$depth++} elseif($c -eq ')'){ $depth--; if($depth -eq 0){$close=$i;break} }
  }
  if($close -lt 0){return @()}
  $txt=$sig.Substring($open+1,$close-$open-1).Trim(); if(-not $txt){return @()}

  $items=New-Object System.Collections.Generic.List[string]
  $cur='';$angle=0;$paren=0;$square=0
  foreach($ch in $txt.ToCharArray()){
    switch($ch){
      '<'{$angle++;$cur+=$ch;continue}
      '>'{if($angle -gt 0){$angle--};$cur+=$ch;continue}
      '('{$paren++;$cur+=$ch;continue}
      ')' {if($paren -gt 0){$paren--};$cur+=$ch;continue}
      '['{$square++;$cur+=$ch;continue}
      ']' {if($square -gt 0){$square--};$cur+=$ch;continue}
      ','{
        if($angle -eq 0 -and $paren -eq 0 -and $square -eq 0){$p=$cur.Trim();if($p){$items.Add($p)};$cur='';continue}
        $cur+=$ch;continue
      }
      default{$cur+=$ch}
    }
  }
  $tail=$cur.Trim(); if($tail){$items.Add($tail)}

  $names=New-Object System.Collections.Generic.List[string]
  foreach($item in $items){
    $clean=[regex]::Replace($item,'@[\w$.]+(\([^)]*\))?\s*','')
    $tokens=@([regex]::Split($clean.Trim(),'\s+')|?{$_ -ne ''})
    if($tokens.Count -eq 0){continue}
    $n=[string]$tokens[$tokens.Count-1]; $n=$n -replace '\.{3}',''; $n=$n -replace '\[\]$',''
    if($n -match '^[A-Za-z_][A-Za-z0-9_]*$'){$names.Add($n)}
  }
  return $names
}

function ParseMethod([string]$sig,$classNames){
  if(-not $sig.Contains('(')){return $null}
  if($sig -match '\b(class|interface|record|enum)\b'){return $null}
  $before=$sig.Substring(0,$sig.IndexOf('(')).Trim(); if(-not $before){return $null}
  $first=([string](($before -split '\s+')[0])).ToLowerInvariant(); if($controlKeywords -contains $first){return $null}
  if($before.Contains('=')){return $null}
  $tokens=@([regex]::Split($before,'\s+')|?{$_ -ne ''}); if($tokens.Count -lt 1){return $null}
  $name=[string]$tokens[$tokens.Count-1]; if($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$'){return $null}
  $prefix=@(); if($tokens.Count -gt 1){$prefix=@($tokens[0..($tokens.Count-2)])}
  $filtered=New-Object System.Collections.Generic.List[string]
  foreach($t in $prefix){$tt=[string]$t; if(-not $tt){continue}; if($modifierKeywords -contains $tt){continue}; if($tt.StartsWith('@')){continue}; if($tt -match '^<.*>$'){continue}; $filtered.Add($tt)}
  $isCtor=$false; if($filtered.Count -eq 0){$isCtor=$true}
  $ret= if($isCtor){''} elseif($filtered.Count -gt 0){$filtered[$filtered.Count-1]} else {''}
  [PSCustomObject]@{Name=$name;Params=(ParseParams $sig);ReturnType=$ret;IsConstructor=$isCtor}
}

function MethodComment([string]$module,$m){
  $context=switch($module){
    'controller'{'该方法位于控制层，负责参数承接、上下文透传和响应封装。'}
    'service'{'该方法位于服务层，负责组织业务步骤并协调上下游依赖。'}
    'config'{'该方法用于配置管理与 Bean 装配，直接影响模块运行效果。'}
    'entity'{'该方法用于维护实体状态，保证持久化对象的一致性与可追踪性。'}
    'repo'{'该方法用于定义仓储查询语义，执行逻辑由框架按命名规则生成。'}
    'security'{'该方法用于认证授权处理，确保访问链路符合安全策略。'}
    'filter'{'该方法运行在过滤链上，用于处理请求前置校验和响应后置动作。'}
    default{'该方法遵循当前模块约定，承担明确的输入处理与结果输出职责。'}
  }
  $n=$m.Name; $lower=$n.ToLowerInvariant();
  $ls=New-Object System.Collections.Generic.List[string];
  $ls.Add('/**')
  if($m.IsConstructor){
    $ls.Add(" * 构造并初始化 $n 对象。");
    $ls.Add(' * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。')
  } elseif($lower.StartsWith('get')){
    $f=if($n.Length -gt 3){$n.Substring(3)}else{'目标字段'}
    $ls.Add(" * 获取 $f 字段值。");
    $ls.Add(' * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。')
  } elseif($lower.StartsWith('set')){
    $f=if($n.Length -gt 3){$n.Substring(3)}else{'目标字段'}
    $ls.Add(" * 设置 $f 字段值。");
    $ls.Add(' * 该方法统一字段写入入口，便于后续扩展校验、审计或联动逻辑。')
  } elseif($lower.StartsWith('is') -or $lower.StartsWith('has')){
    $ls.Add(" * 执行 $n 条件判断。");
    $ls.Add(' * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。')
  } else {
    $ls.Add(" * 执行 $n 业务处理。");
    $ls.Add(' * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。')
  }
  $ls.Add(" * $context")
  foreach($p in $m.Params){$ls.Add(" * @param $p 输入参数 $p，用于参与本次处理流程。")}
  if(-not $m.IsConstructor -and $m.ReturnType -and $m.ReturnType -ne 'void'){
    if($m.ReturnType -eq 'boolean' -or $m.ReturnType -eq 'Boolean'){$ls.Add(' * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。')}
    else{$ls.Add(' * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。')}
  }
  $ls.Add(' */')
  return $ls
}

function Delta([string]$line){(([regex]::Matches($line,'\{')).Count)-(([regex]::Matches($line,'\}')).Count)}

$changed=0
foreach($f in $files){
  $lines=Get-Content -Path $f.FullName -Encoding UTF8
  $new=New-Object System.Collections.Generic.List[string]
  $module=GetModule $f.FullName
  $brace=0
  $classStack=New-Object System.Collections.Generic.List[object]

  for($i=0;$i -lt $lines.Count;$i++){
    $line=$lines[$i]; $trim=$line.Trim()
    $atMember=($classStack.Count -gt 0 -and $brace -eq $classStack[$classStack.Count-1].BodyDepth)

    if($atMember -and $trim.Contains('(') -and -not $trim.StartsWith('@') -and -not $trim.StartsWith('//') -and -not $trim.StartsWith('*') -and -not $trim.StartsWith('/*')){
      $prefix=$trim.Substring(0,$trim.IndexOf('('));
      if(-not $prefix.Contains('=') -and $trim -notmatch '^(if|for|while|switch|catch|return|throw|new|else|do|try)\b'){
        $sig=CollectSig $lines $i
        $classNames=@($classStack|%{$_.Name})
        $m=ParseMethod $sig.Text $classNames
        if($m -ne $null -and -not (HasDoc $lines $i)){
          $indent=([regex]::Match($line,'^\s*')).Value
          foreach($c in (MethodComment $module $m)){ $new.Add($indent + $c) }
        }
      }
    }

    # javadoc 格式修复：块内非 * 行补齐 * 前缀
    if($trim.StartsWith('/**') -or ($trim.StartsWith('/*') -and -not $trim.StartsWith('/**'))){
      $new.Add($line)
      if($trim.Contains('*/')){ } else {
        $j=$i+1
        while($j -lt $lines.Count){
          $l2=$lines[$j]; $t2=$l2.Trim();
          if($t2.StartsWith('*/')){ $new.Add($l2); $i=$j; break }
          if($t2 -eq ''){ $new.Add($l2) }
          elseif($t2.StartsWith('*')){ $new.Add($l2) }
          else{
            $indent2=([regex]::Match($l2,'^\s*')).Value
            $new.Add($indent2 + ' * ' + $t2)
          }
          $j++
        }
      }
    } else {
      $new.Add($line)
    }

    if($trim -match '^(?:public|protected|private|abstract|final|sealed|non-sealed|static|\s)*\b(class|interface|record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)'){
      if($line.Contains('{')){ $classStack.Add([PSCustomObject]@{Name=$Matches[2];BodyDepth=$brace+1}) }
    }
    $brace += Delta $line
    while($classStack.Count -gt 0 -and $brace -lt $classStack[$classStack.Count-1].BodyDepth){$classStack.RemoveAt($classStack.Count-1)}
  }

  $oldText=($lines -join "`n"); $newText=($new -join "`n")
  if($oldText -ne $newText){ Set-Content -Path $f.FullName -Value $newText -Encoding UTF8; $changed++ }
}
"PATCHED_FILES=$changed"
