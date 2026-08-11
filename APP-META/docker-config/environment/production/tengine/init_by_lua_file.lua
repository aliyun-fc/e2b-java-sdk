local init_by_lua_file_list = {
    --"/opt/taobao/tengine/conf/gray_conf/lua_script/engine_init.lua",
    "/etc/tengine/tmd4_loc.lua",
}


for i, f in ipairs(init_by_lua_file_list) do
    f, err = loadfile(f)
    if err then
        ngx.log(ngx.ERR, "load lua file failed:", err)
    else
        f()
    end
end