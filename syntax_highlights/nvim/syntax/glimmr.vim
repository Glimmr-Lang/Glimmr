" Vim syntax file
" Language:     Glimmir
" Filenames:    *.glimmir


" quit when a syntax file was already loaded
if exists("b:current_syntax")
  finish
endif

" Disable spell checking of syntax.
syn spell notoplevel

" glimmir is case sensitive.

" lowercase identifier - the standard way to match
" syn match    glimmirLCIdentifier /\<\(\l\|_\)\(\w\|'\)*\>/

syn match    glimmirKeyChar    "|"

" Some convenient clusters
syn cluster  glimmirAllErrs contains=glimmirBraceErr,glimmirBrackErr,glimmirParenErr,glimmirCommentErr,glimmirEndErr,glimmirThenErr

syn cluster  glimmirAENoParen contains=glimmirBraceErr,glimmirBrackErr,glimmirCommentErr,glimmirEndErr,glimmirThenErr

syn cluster  glimmirContained contains=glimmirTodo,glimmirPreDef,glimmirModParam,glimmirModParam1,glimmirPreMPRestr,glimmirMPRestr,glimmirMPRestr1,glimmirMPRestr2,glimmirMPRestr3,glimmirModRHS,glimmirFuncWith,glimmirFuncStruct,glimmirModTypeRestr,glimmirModTRWith,glimmirWith,glimmirWithRest,glimmirModType,glimmirFullMod


" Enclosing delimiters
syn region   glimmirEncl transparent matchgroup=glimmirKeyword start="(" matchgroup=glimmirKeyword end=")" contains=ALLBUT,@glimmirContained,glimmirParenErr
syn region   glimmirEncl transparent matchgroup=glimmirKeyword start="{" matchgroup=glimmirKeyword end="}"  contains=ALLBUT,@glimmirContained,glimmirBraceErr
syn region   glimmirEncl transparent matchgroup=glimmirKeyword start="\[" matchgroup=glimmirKeyword end="\]" contains=ALLBUT,@glimmirContained,glimmirBrackErr
syn region   glimmirEncl transparent matchgroup=glimmirKeyword start="#\[" matchgroup=glimmirKeyword end="\]" contains=ALLBUT,@glimmirContained,glimmirBrackErr


" Comments
syn region glimmirComment start="\/\/" end="$" contains=glimmirComment,glimmirTodo,@Spell
" syn region   glimmirComment start="\%%" contains=glimmirComment,glimmirTodo,@Spell
syn keyword  glimmirTodo contained TODO FIXME

syn keyword  glimmirKeyword  type loop 
syn keyword  glimmirKeyword  import if else match 
syn keyword  glimmirKeyword  let in fn where

syn keyword  glimmirType Number String Boolean Unit Promise

syn keyword  glimmirBoolean      true false
syn match    glimmirConstructor  "(\s*)"
syn match    glimmirConstructor  "\[\s*\]"
syn match    glimmirConstructor  "#\[\s*\]"
syn match    glimmirConstructor  "\u\(\w\|'\)*\>"

syn match glimmirFnIdent "[a-zA-Z_][a-zA-Z0-9_]*\s*\ze("

" Module prefix
syn match    glimmirModPath      "\u\(\w\|'\)*\."he=e-1

syn match    glimmirCharacter    +#"\\""\|#"."\|#"\\\d\d\d"+
syn match    glimmirCharErr      +#"\\\d\d"\|#"\\\d"+
syn region   glimmirString       start=+"+ skip=+\\\\\|\\"+ end=+"+ contains=@Spell

syn match    glimmirFunDef       "=>"
syn match    glimmirOperator     "::"
syn match    glimmirAnyVar       "\<_\>"
syn match    glimmirKeyChar      "!"
syn match    glimmirKeyChar      ";"
syn match    glimmirKeyChar      "\*"
syn match    glimmirKeyChar      "="

syn match    glimmirNumber        "\<-\=\d\+\>"
syn match    glimmirNumber        "\<-\=0[x|X]\x\+\>"
syn match    glimmirReal          "\<-\=\d\+\.\d*\([eE][-+]\=\d\+\)\=[fl]\=\>"

" Synchronization
syn sync minlines=20
syn sync maxlines=500

hi def link glimmirComment      Comment

hi def link glimmirModPath      Include
hi def link glimmirModule       Include
hi def link glimmirModParam1    Include
hi def link glimmirModType      Include
hi def link glimmirMPRestr3     Include
hi def link glimmirFullMod      Include
hi def link glimmirModTypeRestr Include
hi def link glimmirWith         Include
hi def link glimmirMTDef        Include

hi def link glimmirConstructor  Constant

hi def link glimmirModPreRHS    Keyword
hi def link glimmirMPRestr2     Keyword
hi def link glimmirKeyword      Keyword
hi def link glimmirFunDef       Keyword
hi def link glimmirRefAssign    Keyword
hi def link glimmirKeyChar      Keyword
hi def link glimmirAnyVar       Keyword
hi def link glimmirTopStop      Keyword
hi def link glimmirOperator     Keyword
hi def link glimmirThread       Keyword

hi def link glimmirBoolean      Boolean
hi def link glimmirAtom         Boolean
hi def link glimmirCharacter    Character
hi def link glimmirNumber       Number
hi def link glimmirReal         Float
hi def link glimmirString       String
hi def link glimmirType         Type
hi def link glimmirTodo         Todo
hi def link glimmirEncl         Keyword
hi def link glimmirFnIdent      Function

let b:current_syntax = "glimmir"

" vim: ts=8
