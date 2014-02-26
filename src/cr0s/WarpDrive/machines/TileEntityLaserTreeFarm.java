package cr0s.WarpDrive.machines;

import java.util.ArrayList;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import cr0s.WarpDrive.Vector3;
import cr0s.WarpDrive.WarpDrive;
import cr0s.WarpDrive.WarpDriveConfig;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.IPeripheral;

public class TileEntityLaserTreeFarm extends TileEntityAbstractMiner implements IPeripheral
{
	
	Boolean active = false;
	
	private int mode = 0;
	private boolean doLeaves = false;
	private boolean silkTouchLeaves = false;
	
	private final int defSize = 8;
	private final int scanWait = 40;
	private final int mineWait = 4;
	private int delayMul = 4;
	
	private int totalHarvested=0;
	
	private int scan=0;
	private int xSize = defSize;
	private int zSize = defSize;
	
	ArrayList<Vector3> logs;
	private int logIndex = 0;
	
	private String[] methodsArray = {
			"start",
			"stop",
			"area",
			"leaves",
			"silkTouch",
			"silkTouchLeaves",
			"state"
	};
	
	public TileEntityLaserTreeFarm()
	{
		super();
	}
	
	@Override
	public void updateEntity()
	{
		super.updateEntity();
		
		if(active)
		{
			if(mode == 0)
			{	
				if(++scan >= scanWait)
				{
					scan = 0;
					logs = scanTrees();
					if(logs.size() > 0)
						mode = 1;
					logIndex = 0;
				}
			}
			else
			{
				if(++scan >= mineWait * delayMul)
				{
					scan = 0;
					int cost = calculateBlockCost();
					if(collectEnergyPacketFromBooster(cost,true))
					{
						if(logIndex >= logs.size())
						{
							mode = 0;
							return;
						}
						Vector3 pos = logs.get(logIndex);
						int blockID = worldObj.getBlockId(pos.intX(), pos.intY(), pos.intZ());
						if(isLog(blockID) || (doLeaves && isLeaf(blockID)))
						{
							delayMul = 1;
							if(isRoomForHarvest())
							{
								if(collectEnergyPacketFromBooster(cost,false))
								{
									if(isLog(blockID))
										delayMul = 4;
									totalHarvested++;
									harvestBlock(pos);
								}
								else
									return;
							}
							else
								return;
						}
						logIndex++;
					}
				}
			}
		}
	}
	
	private boolean isLog(int blockID)
	{
		return WarpDriveConfig.i.MinerLogs.contains(blockID);
	}
	
	private boolean isLeaf(int blockID)
	{
		return WarpDriveConfig.i.MinerLeaves.contains(blockID);
	}
	
	private void addTree(ArrayList<Vector3> list,Vector3 newTree)
	{
		WarpDrive.debugPrint("Adding tree position:" + newTree.x + "," + newTree.y + "," + newTree.z);
		list.add(newTree);
	}
	
	private ArrayList<Vector3> scanTrees()
	{
		int xmax, zmax, x1, x2, z1, z2;
		int xmin, zmin;
		x1 = xCoord + xSize / 2;
		x2 = xCoord - xSize / 2;
		xmin = Math.min(x1, x2);
		xmax = Math.max(x1, x2);

		z1 = zCoord + zSize / 2;
		z2 = zCoord - zSize / 2;
		zmin = Math.min(z1, z2);
		zmax = Math.max(z1, z2);
		
		ArrayList<Vector3> logPositions = new ArrayList<Vector3>();
		
		for(int x=xmin;x<=xmax;x++)
		{
			for(int z=zmin;z<=zmax;z++)
			{
				int blockID = worldObj.getBlockId(x, yCoord, z);
				if(isLog(blockID))
				{
					Vector3 pos = new Vector3(x,yCoord,z);
					logPositions.add(pos);
					scanNearby(logPositions,x,yCoord,z,0);
				}
			}
		}
		return logPositions;
	}
	
	private void scanNearby(ArrayList<Vector3> current,int x,int y,int z,int d)
	{
		int[] deltas = {0,-1,1};
		for(int dx : deltas)
		{
			for(int dy=1;dy>=0;dy--)
			{
				for(int dz : deltas)
				{
					int blockID = worldObj.getBlockId(x+dx, y+dy, z+dz);
					if(isLog(blockID) || (doLeaves && isLeaf(blockID)))
					{
						Vector3 pos = new Vector3(x+dx,y+dy,z+dz);
						if(!current.contains(pos))
						{
							addTree(current,pos);
							if(d < 35)
								scanNearby(current,x+dx,y+dy,z+dz,d+1);
						}
					}
				}	
			}
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound tag)
	{
		super.writeToNBT(tag);
		tag.setInteger("xSize", xSize);
		tag.setInteger("zSize", zSize);
		tag.setBoolean("doLeaves", doLeaves);
		tag.setBoolean("active", active);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound tag)
	{
		super.readFromNBT(tag);
		xSize = tag.getInteger("xSize");
		zSize = tag.getInteger("zSize");
		defineMiningArea(xSize,zSize);
		
		doLeaves = tag.getBoolean("doLeaves");
		active   = tag.getBoolean("active");
	}
	
	@Override
	public boolean shouldChunkLoad()
	{
		return active;
	}

	@Override
	public String getType()
	{
		return "treefarmLaser";
	}

	@Override
	public String[] getMethodNames()
	{
		return methodsArray;
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws Exception
	{
		String methodStr = methodsArray[method];
		if(methodStr == "start")
		{
			if(!active)
			{
				mode = 0;
				totalHarvested = 0;
				active = true;
			}
			return new Boolean[] { true };
		}
		
		if(methodStr == "stop")
		{
			active = false;
		}
		
		if(methodStr == "area")
		{
			try
			{
				if(arguments.length == 1)
				{
					xSize = toInt(arguments[0]);
					zSize = xSize;
				}
				else if(arguments.length == 2)
				{
					xSize = toInt(arguments[0]);
					zSize = toInt(arguments[1]);
				}
			}
			catch(NumberFormatException e)
			{
				xSize = defSize;
				zSize = defSize;
			}
			defineMiningArea(xSize,zSize);
			return new Integer[] { xSize , zSize };
			
		}
		
		if(methodStr == "leaves")
		{
			try
			{
				if(arguments.length > 0)
					doLeaves = toBool(arguments[0]);
			}
			catch(Exception e)
			{
				
			}
			return new Boolean[] { doLeaves };
		}
		
		if(methodStr == "silkTouch")
		{
			try
			{
				silkTouch(arguments[0]);
			}
			catch(Exception e)
			{
				silkTouch(false);
			}
			return new Object[] { silkTouch() };
		}
		
		if(methodStr == "silkTouchLeaves")
		{
			try
			{
				silkTouchLeaves = toBool(arguments[0]);
			}
			catch(Exception e)
			{
				silkTouchLeaves = false;
			}
			return new Object[] { silkTouchLeaves };
		}
		
		if(methodStr == "state")
		{
			String state = active ? "active" : "inactive";
			return new Object[] { state, xSize,zSize,energy(),totalHarvested };
		}
		return null;
	}

	@Override
	public boolean canAttachToSide(int side)
	{
		return true;
	}

	@Override
	public void attach(IComputerAccess computer)
	{
	}

	@Override
	public void detach(IComputerAccess computer)
	{
	}
	
	//ABSTRACT LASER IMPLEMENTATION
	@Override
	protected boolean silkTouch(int blockID)
	{
		if(isLeaf(blockID))
			return silkTouchLeaves;
		return silkTouch();
	}
	
	@Override
	protected boolean canSilkTouch()
	{
		return true;
	}

	@Override
	protected int minFortune()
	{
		return 0;
	}

	@Override
	protected int maxFortune()
	{
		return 0;
	}

	@Override
	protected double laserBelow()
	{
		return -0.5;
	}

	@Override
	protected float getColorR()
	{
		return 0.2f;
	}

	@Override
	protected float getColorG()
	{
		return 0.7f;
	}

	@Override
	protected float getColorB()
	{
		return 0.4f;
	}
	
}
