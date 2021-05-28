local prefs = inline.prefs("notes")
local list = prefs:getstringset("notes", {})

local function find(t, e)
	for n, v in pairs(t) do
		if v == e then
			return n
		end
	end
end

function save(context, name)
	prefs[name] = context:gettext():gsub("%{save .*%}%$", "")
	if not find(list, name) then
		list[#list + 1] = name
		prefs.notes = list
	end
	return ""
end

function delnote(context, name)
	local index = find(list, name)
	if index then
		prefs[name] = nil
		list[index] = nil
		prefs.notes = list
	end
	return ""
end

function note(context, name)
	if find(list, name) then
	 	return prefs[name]
	else
		return ""
	end
end

function notes(context)
	local result = "Notes:"
	for k, v in ipairs(list) do
		result = result .. ("– %s\n"):format(v)
	end
	return result
end
