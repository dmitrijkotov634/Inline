function exec(arg)
    return load(arg)(arg)
end

function eval(arg)
    return load("return " .. arg)(arg)
end
