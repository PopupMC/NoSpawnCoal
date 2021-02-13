## No Spawn Coal

Spawn is off-limits to newbie players because most refuse to play an smp-game but still want to do non-smp stuff on my 
server. I disabled access to `spawn` and disabled the `/spawn` command only to newbie players but they still manage to 
find their way there.

So I devised this plugin, NoSpawnCoal (Badly Named, `Coal Rank` is newbie)

* It's the only plugin I have that's designed for servers of any size, even large servers
* It checks permissions for every single player every single second who is at spawn (But in a non-laggy way)
* It limits processing to 25 players per loop
* It loops once a second, but auto-scales to once per tick temporarily on large player fluctuations
* It grants temporary immunity to new players so that other plugins can work with new players (To prevent plugin conflict)
* It sets a hard limit on players to check for really, really large player fluctuations (In other words outright 
skipping player checks beyond a hard limit). This keeps the entire server running smoothly and lag free and ensures all 
checks finish on time before the next loop.
* It does simpler immunity checking and skips most immunity processing when in expidited mode (1 loop per tick on larger player fluctuations)
* It costs little to no memory and no processing power when no players are to check

Right now it handles up to 475 players at spawn all at the same time, anymore and they don't get checked for reasons 
listed above. The plugin plans to process 25 people per second but if more need processing it will auto switch to 
expedited mode where it processes 25 people per tick instead of per second. In Expidited mode, 25 people per tick means 
it finishes up to 475 people across 19 ticks meaning it can start in regular mode on the very next normal loop having no
overlap.

When maxed out at 475 players being checked, it costs no more than 15KB of memory which is a breeze for any server.

Normally I would fill out a lot of into here but this is an internal plugin
so maybe later.

License: Do whatever you want as long as you credit me back (Apache 2)

Contributions always welcome, fork and send pull request.

More plugins to come.
