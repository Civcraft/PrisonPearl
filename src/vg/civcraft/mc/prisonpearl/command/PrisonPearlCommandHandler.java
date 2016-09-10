package vg.civcraft.mc.prisonpearl.command;

import vg.civcraft.mc.civmodcore.command.CommandHandler;
import vg.civcraft.mc.prisonpearl.command.commands.*;

public class PrisonPearlCommandHandler extends CommandHandler{

	@Override
	public void registerCommands() {
		addCommands(new Broadcast("Broadcast"));
		addCommands(new ConfirmBroadcast("Confirm"));
		addCommands(new Feed("Feed"));
		addCommands(new Free("Free"));
		addCommands(new FreeAny("FreeAny"));
		addCommands(new ImprisonAny("ImprisonAny"));
		addCommands(new Kill("Kill"));
		addCommands(new Locate("Locate"));
		addCommands(new LocateAny("LocateAny"));
		addCommands(new Return("Return"));
		addCommands(new SetDamage("SetDamage"));
		addCommands(new SetDistance("SetDistance"));
		addCommands(new SetMotd("SetMotd"));
		addCommands(new SummonCommand("Summon"));
		addCommands(new ToggleBlocks("ToggleBlocks"));
		addCommands(new ToggleDamage("ToggleDamage"));
		addCommands(new ToggleSpeech("ToggleSpeech"));
		addCommands(new WorldBorder("WorldBorder"));
	}

}
