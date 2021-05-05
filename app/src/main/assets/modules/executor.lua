function exec(context, arg)
    return load(arg)()
end

function eval(context, arg)
    return load("return " .. arg)()
end
