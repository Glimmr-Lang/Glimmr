-- Zulu neovim plugin

local function setup()
    vim.cmd([[
        autocmd BufRead,BufNewFile *.glmr set filetype=glimmr
        autocmd Syntax glimmr runtime! syntax/glimmr.vim
    ]])
end

return {
    setup = setup,
}
