execute in minecraft:overworld store result score #now fog_world_day run time query day
scoreboard players add #now fog_world_day 1
execute as @a unless score @s fog_world_day = #now fog_world_day run title @s actionbar [{"text":"День ","color":"gold"},{"score":{"name":"#now","objective":"fog_world_day"},"color":"yellow"}]
execute as @a run scoreboard players operation @s fog_world_day = #now fog_world_day
schedule function frontier_days:clock 1s replace
