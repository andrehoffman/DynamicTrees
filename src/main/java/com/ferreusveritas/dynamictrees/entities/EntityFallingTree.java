package com.ferreusveritas.dynamictrees.entities;

import java.util.ArrayList;
import java.util.List;

import com.ferreusveritas.dynamictrees.blocks.BlockBranch.BlockItemStack;
import com.ferreusveritas.dynamictrees.util.BranchDestructionData;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EntityFallingTree extends Entity {

	public static final DataParameter<String> nameParameter = EntityDataManager.createKey(EntityFallingTree.class, DataSerializers.STRING);
	
	protected BranchDestructionData destroyData;
	protected List<ItemStack> payload = new ArrayList<>();
	protected Vec3d geomCenter = Vec3d.ZERO;
	protected Vec3d massCenter = Vec3d.ZERO;
	private boolean activated = false;
	
	public EntityFallingTree(World worldIn) {
		super(worldIn);
		setSize(1.0f, 1.0f);
	}
	
	public boolean isActivated() {
		return activated;
	}
	
	public void setData(BranchDestructionData destroyData, List<ItemStack> payload) {
		this.activated = true;
		this.destroyData = destroyData;
		BlockPos cutPos = destroyData.cutPos;
		this.payload = payload;
		
		setPooka("naneet");
		
		this.posX = cutPos.getX() + 0.5;
		this.posY = cutPos.getY();
		this.posZ = cutPos.getZ() + 0.5;

		int numBlocks = destroyData.getNumBranches();
		geomCenter = new Vec3d(0, 0, 0);
		AxisAlignedBB aabb = new AxisAlignedBB(cutPos);
		double totalMass = 0;
		
		//Calculate center of geometry, center of mass and bounding box, remap to relative coordinates
		for(int index = 0; index < destroyData.getNumBranches(); index++) {
			BlockPos relPos = destroyData.getBranchRelPos(index);
			BlockPos absPos = cutPos.add(relPos);
			
			aabb = aabb.union(new AxisAlignedBB(absPos));
			
			int radius = destroyData.getBranchRadius(index);
			float mass = (radius * radius * 64) / 4096f;//Assume full height cuboids for simplicity
			totalMass += mass;
			
			Vec3d relVec = new Vec3d(relPos.getX(), relPos.getY(), relPos.getZ());
			geomCenter = geomCenter.add(relVec);
			massCenter = massCenter.add(relVec.scale(mass));
		}

		this.setEntityBoundingBox(aabb);
		geomCenter = geomCenter.scale(1.0 / numBlocks);
		massCenter = massCenter.scale(1.0 / totalMass);
		
		initMotion();
	}
	
	public String getPooka() {
		return getDataManager().get(nameParameter);
	}
	
	public void setPooka(String name) {
		if(!world.isRemote) {
			getDataManager().set(nameParameter, name);
		}
	}
	
	public BranchDestructionData getDestroyData() {
		return destroyData;
	}
	
	public Vec3d getGeomCenter() {
		return geomCenter;
	}
	
	public Vec3d getMassCenter() {
		return massCenter;
	}
	
	@Override
	public void setPosition(double x, double y, double z) {
		//This comes to the client as a packet from the server.
		double dx = x - this.posX;
		double dy = y - this.posY;
		double dz = z - this.posZ;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.setEntityBoundingBox(getEntityBoundingBox().offset(dx, dy, dz));
	}
	
	@Override
	public void onEntityUpdate() {
		if(!isDead) {
			if(!activated) {//This only happens on the client
				transferEntityData();
			}

			prevPosX = posX;
			prevPosY = posY;
			prevPosZ = posZ;

			handleMotion();
			
			setEntityBoundingBox(getEntityBoundingBox().offset(motionX, motionY, motionZ));

			if(ticksExisted > 30) {
				dropPayLoad();
				setDead();
			}
		}
	}
	
	public void transferEntityData() {
		List<EntityFallingTree> entities = world.getEntitiesWithinAABB(this.getClass(), new AxisAlignedBB(posX, posY, posZ, posX, posY, posZ).grow(0.5));
		for(EntityFallingTree tree : entities) {
			if(tree != this) {
				this.destroyData = tree.destroyData;
				this.payload = tree.payload;
				this.geomCenter = tree.geomCenter;
				this.massCenter = tree.massCenter;
				this.activated = true;
				this.setEntityBoundingBox(tree.getEntityBoundingBox());
				tree.setDead();
			}
		}
	}
	
	public void initMotion() {
		motionY = 0.5;
	}
	
	public void handleMotion() {
		motionY -= 0.03;//Gravity
		//motionY = 0.0;
		posX += motionX;
		posY += motionY;
		posZ += motionZ;
		rotationYaw += 10;
	}
	
	public void dropPayLoad() {		
		if(!world.isRemote) {
			BlockPos pos = new BlockPos(posX, posY, posZ);
			payload.forEach(i -> Block.spawnAsEntity(world, pos, i));
		
			for(BlockItemStack bis : destroyData.leavesDrops) {
				BlockPos sPos = pos.add(bis.pos);
				EntityItem itemEntity = new EntityItem(world, sPos.getX() + 0.5, sPos.getY() + 0.5, sPos.getZ() + 0.5, bis.stack);
				world.spawnEntity(itemEntity);
			}
		}
	}
	
	@Override
	protected void entityInit() {
		getDataManager().register(nameParameter, "kook");
	}
	
	@Override
	protected void readEntityFromNBT(NBTTagCompound compound) {
		//compound.setString("name", name);
	}
	
	@Override
	protected void writeEntityToNBT(NBTTagCompound compound) {
		//name = compound.getString("name");
	}
	
}