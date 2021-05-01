function exec(context, arg)
    return load(arg)(arg)
end

function eval(context, arg)
    return load("return " .. arg)(arg)
end
