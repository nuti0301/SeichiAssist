package com.github.unchama.seichiassist.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.projectiles.ProjectileSource;

import com.github.unchama.seichiassist.ActiveSkill;
import com.github.unchama.seichiassist.ActiveSkillEffect;
import com.github.unchama.seichiassist.SeichiAssist;
import com.github.unchama.seichiassist.data.Coordinate;
import com.github.unchama.seichiassist.data.PlayerData;
import com.github.unchama.seichiassist.task.CondenSkillTaskRunnable;
import com.github.unchama.seichiassist.util.ExperienceManager;
import com.github.unchama.seichiassist.util.Util;

public class EntityListener implements Listener {
	SeichiAssist plugin = SeichiAssist.plugin;
	HashMap<UUID,PlayerData> playermap = SeichiAssist.playermap;

	@EventHandler
	public void onPlayerActiveSkillEvent(ProjectileHitEvent event){
		//矢を取得する
		final Projectile e = event.getEntity();
		ProjectileSource projsource;
		Player player;


		if(!e.hasMetadata("ArrowSkill")&&!e.hasMetadata("CondenSkill")) {
			return;
		}
		Projectile proj = (Projectile)e;
    	projsource = proj.getShooter();
		if(!(projsource instanceof Player)){
			return;
		}
		player = (Player)projsource;

		if(SeichiAssist.DEBUG){
			player.sendMessage(ChatColor.RED + "ProjectileHitEventの呼び出し");
		}

		//もしサバイバルでなければ処理を終了
		if(!player.getGameMode().equals(GameMode.SURVIVAL)){
			return;
		}


		//壊されるブロックを取得
		Block block = null;
		if(e.hasMetadata("ArrowSkill")){
			block = player.getWorld().getBlockAt(proj.getLocation().add(proj.getVelocity().normalize()));
		}else{
			block = player.getWorld().getBlockAt(proj.getLocation());
		}

		//他人の保護がかかっている場合は処理を終了
		if(!Util.getWorldGuard().canBuild(player, block.getLocation())){
			return;
		}
		//ブロックのタイプを取得
		Material material = block.getType();

		//ブロックタイプがmateriallistに登録されていなければ処理終了
		if(!SeichiAssist.materiallist.contains(material) && e.hasMetadata("ArrowSkill")){
			return;
		}

		//UUIDを取得
		UUID uuid = player.getUniqueId();
		//UUIDを基にプレイヤーデータ取得
		PlayerData playerdata = SeichiAssist.playermap.get(uuid);
		//念のためエラー分岐
		if(playerdata == null){
			player.sendMessage(ChatColor.RED + "playerdataがありません。管理者に報告してください");
			plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "SeichiAssist[blockbreaklistener処理]でエラー発生");
			plugin.getLogger().warning(player.getName() + "のplayerdataがありません。開発者に報告してください");
			return;
		}
		//スキルで破壊されるブロックの時処理を終了
		if(playerdata.activeskilldata.blocklist.contains(block)){
			if(SeichiAssist.DEBUG){
				player.sendMessage("スキルで使用中のブロックです。");
			}
			return;
		}

		//経験値変更用のクラスを設定
		ExperienceManager expman = new ExperienceManager(player);

		//プレイヤーインベントリを取得
		PlayerInventory inventory = player.getInventory();
		//メインハンドとオフハンドを取得
		ItemStack mainhanditem = inventory.getItemInMainHand();
		ItemStack offhanditem = inventory.getItemInOffHand();
		//実際に使用するツールを格納する
		ItemStack tool = null;
		//メインハンドにツールがあるか
		boolean mainhandtoolflag = SeichiAssist.breakmateriallist.contains(mainhanditem.getType());
		//オフハンドにツールがあるか
		boolean offhandtoolflag = SeichiAssist.breakmateriallist.contains(offhanditem.getType());

		//場合分け
		if(mainhandtoolflag){
			//メインハンドの時
			tool = mainhanditem;
		}else if(offhandtoolflag){
			//サブハンドの時
			return;
		}else{
			//どちらにももっていない時処理を終了
			return;
		}

		//耐久値がマイナスかつ耐久無限ツールでない時処理を終了
		if(tool.getDurability() > tool.getType().getMaxDurability() && !tool.getItemMeta().spigot().isUnbreakable()){
			return;
		}


		if(playerdata.activeskilldata.skilltype == ActiveSkill.ARROW.gettypenum()){
			runArrowSkillofHitBlock(player,proj, playerdata.activeskilldata.skillnum, block, tool, expman);
		}else if(playerdata.activeskilldata.skilltype == ActiveSkill.CONDENSE.gettypenum()){
			if(playerdata.activeskilldata.skillnum < 7){
				CondenSkillTaskRunnable.runCondenSkillofExplosion(player,playerdata.activeskilldata.skillnum,block,tool,expman);
			}else{
				CondenSkillTaskRunnable.runCondenSkillofExplosion(player,playerdata.activeskilldata.skillnum - 3 ,block,tool,expman);
			}

			playerdata.activeskilldata.hitflag = true;
		}

		//矢を消滅させる
		event.getEntity().remove();
	}


	private void runArrowSkillofHitBlock(Player player,Projectile proj, int skilllevel,
			Block block, ItemStack tool, ExperienceManager expman) {
		//UUIDを取得
		UUID uuid = player.getUniqueId();
		//playerdataを取得
		PlayerData playerdata = playermap.get(uuid);
		//プレイヤーの向いている方角を取得
		String dir = Util.getCardinalDirection(player);
		//元ブロックのマテリアルを取得
		Material material = block.getType();
		//元ブロックの真ん中の位置を取得
		Location centerofblock = block.getLocation().add(0.5, 0.5, 0.5);

		//壊されるブロックの宣言
		Block breakblock;
		Coordinate start = new Coordinate();
		Coordinate end = new Coordinate();

		//エフェクト用に壊されるブロック全てのリストデータ
		List<Block> breaklist = new ArrayList<Block>();

		//壊される溶岩のリストデータ
		List<Block> lavalist = new ArrayList<Block>();

		switch (dir){
		case "N":
			//北を向いているとき
			if(skilllevel == 4){
				start = new Coordinate(-(skilllevel-3),-(skilllevel-3),-(skilllevel-3)-1);
				end = new Coordinate(skilllevel-3,skilllevel-3,(skilllevel-3)-1);
			}else{
				start = new Coordinate(-(skilllevel-3),-1,-(skilllevel-3)-1);
				end = new Coordinate(skilllevel-3,(skilllevel-5)*2 + 1,(skilllevel-3)-1);
			}
			break;
		case "E":
			//東を向いているとき
			if(skilllevel == 4){
				start = new Coordinate(-(skilllevel-3)+1,-(skilllevel-3),-(skilllevel-3));
				end = new Coordinate((skilllevel-3)+1,skilllevel-3,(skilllevel-3));
			}else{
				start = new Coordinate(-(skilllevel-3)+1,-1,-(skilllevel-3));
				end = new Coordinate((skilllevel-3)+1,(skilllevel-5)*2 + 1,(skilllevel-3));
			}
			break;
		case "S":
			//南を向いているとき
			if(skilllevel == 4){
				start = new Coordinate(-(skilllevel-3),-(skilllevel-3),-(skilllevel-3)+1);
				end = new Coordinate(skilllevel-3,skilllevel-3,(skilllevel-3)+1);
			}else{
				start = new Coordinate(-(skilllevel-3),-1,-(skilllevel-3)+1);
				end = new Coordinate(skilllevel-3,(skilllevel-5)*2 + 1,(skilllevel-3)+1);
			}
			break;
		case "W":
			//西を向いているとき
			if(skilllevel == 4){
				start = new Coordinate(-(skilllevel-3)-1,-(skilllevel-3),-(skilllevel-3));
				end = new Coordinate((skilllevel-3)-1,skilllevel-3,(skilllevel-3));
			}else{
				start = new Coordinate(-(skilllevel-3)-1,-1,-(skilllevel-3));
				end = new Coordinate((skilllevel-3)-1,(skilllevel-5)*2 + 1,(skilllevel-3));
			}
			break;
		case "U":
			//上を向いているとき
			if(skilllevel == 4){
				start = new Coordinate(-(skilllevel-3),0,-(skilllevel-3));
				end = new Coordinate((skilllevel-3),2,(skilllevel-3));
			}else{
				start = new Coordinate(-(skilllevel-3),0,-(skilllevel-3));
				end = new Coordinate((skilllevel-3),(skilllevel-4)*2,(skilllevel-3));
			}
			break;
		case "D":
			//下を向いているとき
			if(skilllevel == 4){
				start = new Coordinate(-(skilllevel-3),-2,-(skilllevel-3));
				end = new Coordinate((skilllevel-3),0,(skilllevel-3));
			}else{
				start = new Coordinate(-(skilllevel-3),-(skilllevel-4)*2,-(skilllevel-3));
				end = new Coordinate((skilllevel-3),0,(skilllevel-3));
			}
			break;
		}

		for(int x = start.x ; x <= end.x ; x++){
			for(int z = start.z ; z <= end.z ; z++){
				for(int y = start.y; y <= end.y ; y++){

					breakblock = block.getRelative(x, y, z);
					//player.sendMessage("x:" + x + "y:" + y + "z:" + z + "Type:"+ breakblock.getType().name());
					//もし壊されるブロックがもともとのブロックと同じ種類だった場合
					if(breakblock.getType().equals(material)
							|| (block.getType().equals(Material.DIRT)&&breakblock.getType().equals(Material.GRASS))
							|| (block.getType().equals(Material.GRASS)&&breakblock.getType().equals(Material.DIRT))
							|| (block.getType().equals(Material.GLOWING_REDSTONE_ORE)&&breakblock.getType().equals(Material.REDSTONE_ORE))
							|| (block.getType().equals(Material.REDSTONE_ORE)&&breakblock.getType().equals(Material.GLOWING_REDSTONE_ORE))
							|| breakblock.getType().equals(Material.LAVA)
							){
						if(Util.canBreak(player, breakblock)){
							if(breakblock.getType().equals(Material.LAVA)){
								lavalist.add(breakblock);
							}else{
								breaklist.add(breakblock);
							}
						}

					}
				}
			}
		}

		//減る経験値計算

		//実際に破壊するブロック数  * 全てのブロックを破壊したときの消費経験値÷すべての破壊するブロック数
		double useExp = (double) (breaklist.size())
				* ActiveSkill.getActiveSkillUseExp(playerdata.activeskilldata.skilltype, playerdata.activeskilldata.skillnum)
				/((end.x - start.x + 1) * (end.z - start.z + 1) * (end.y - start.y + 1)) ;
		if(SeichiAssist.DEBUG){
			player.sendMessage(ChatColor.RED + "必要経験値：" + ActiveSkill.getActiveSkillUseExp(playerdata.activeskilldata.skilltype, playerdata.activeskilldata.skillnum));
			player.sendMessage(ChatColor.RED + "全ての破壊数：" + ((end.x - start.x + 1) * (end.z - start.z + 1) * (end.y - start.y + 1)));
			player.sendMessage(ChatColor.RED + "実際の破壊数：" + breaklist.size());
			player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要な経験値：" + useExp);
		}
		//減る耐久値の計算
		short durability = (short) (tool.getDurability() + Util.calcDurability(tool.getEnchantmentLevel(Enchantment.DURABILITY),breaklist.size()));
		//１マス溶岩を破壊するのにはブロック１０個分の耐久が必要
		if(lavalist.size() == 1){
			durability += Util.calcDurability(tool.getEnchantmentLevel(Enchantment.DURABILITY),10);
		}


		//実際に経験値を減らせるか判定
		if(!expman.hasExp(useExp)){
			//デバッグ用
			if(SeichiAssist.DEBUG){
				player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要な経験値が足りません");
			}
			return;
		}
		if(SeichiAssist.DEBUG){
			player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なツールの耐久値:" + durability);
		}
		//実際に耐久値を減らせるか判定
		if(tool.getType().getMaxDurability() <= durability && !tool.getItemMeta().spigot().isUnbreakable()){
			//デバッグ用
			if(SeichiAssist.DEBUG){
				player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なツールの耐久値が足りません");
			}
			return;
		}


		//経験値を減らす
		expman.changeExp(-useExp);

		//耐久値を減らす
		tool.setDurability(durability);


		//以降破壊する処理

		playerdata.activeskilldata.blocklist = breaklist;


		//１マスの溶岩のみ破壊する処理
		if(lavalist.size() < 10){
			for(int lavanum = 0 ; lavanum <lavalist.size();lavanum++){
				lavalist.get(lavanum).setType(Material.AIR);
			}
		}

		//選択されたブロックを破壊する処理

		//エフェクトが指定されていないときの処理
		if(playerdata.activeskilldata.effectnum == 0){
			for(Block b:breaklist){
				Util.BreakBlock(player, b, player.getLocation(), tool,true);
			}
			playerdata.activeskilldata.blocklist.clear();
		}
		//エフェクトが指定されているときの処理
		else{
			ActiveSkillEffect[] skilleffect = ActiveSkillEffect.values();
			skilleffect[playerdata.activeskilldata.effectnum - 1].runArrowEffect(breaklist, start, end);
		}
		playerdata.activeskilldata.blocklist.clear();

	}


	@EventHandler
	public void onEntityExplodeEvent(EntityExplodeEvent event){
		Entity e = event.getEntity();
	    if ( e instanceof Projectile && e.hasMetadata("ArrowSkill") ) {
	    	event.setCancelled(true);
	    }else if( e instanceof Projectile && e.hasMetadata("CondenSkill")){
	    	event.setCancelled(true);
	    }
	}


	@EventHandler
	public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event){
		Entity e = event.getDamager();
	    if ( e instanceof Projectile && e.hasMetadata("ArrowSkill") ) {
	    	event.setCancelled(true);
	    }else if( e instanceof Projectile && e.hasMetadata("CondenSkill")){
	    	event.setCancelled(true);
	    }
	}

	@EventHandler
	public void PvPToggleEvent(EntityDamageByEntityEvent event){
		Entity damager = event.getDamager();
		Entity entity = event.getEntity();
		if(damager instanceof Player && entity instanceof Player){
			UUID uuid_damager = damager.getUniqueId();
			PlayerData playerdata_damager = playermap.get(uuid_damager);
			//念のためエラー分岐
			if(playerdata_damager == null){
				damager.sendMessage(ChatColor.RED + "playerdataがありません。管理者に報告してください");
				plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "SeichiAssist[PvP処理]でエラー発生");
				plugin.getLogger().warning(damager.getName()+ "のplayerdataがありません。開発者に報告してください");
				return;
			}
			if(!playerdata_damager.pvpflag){
				event.setCancelled(true);
				return;
			}

			UUID uuid_entity = entity.getUniqueId();
			PlayerData playerdata_entity = playermap.get(uuid_entity);
			//念のためエラー分岐
			if(playerdata_entity == null){
				entity.sendMessage(ChatColor.RED + "playerdataがありません。管理者に報告してください");
				plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "SeichiAssist[PvP処理]でエラー発生");
				plugin.getLogger().warning(entity.getName()+ "のplayerdataがありません。開発者に報告してください");
				return;
			}
			if(!playerdata_entity.pvpflag){
				event.setCancelled(true);
				return;
			}
		}
	}
}
