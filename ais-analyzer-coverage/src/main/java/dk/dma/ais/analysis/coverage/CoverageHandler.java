/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.ais.analysis.coverage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.dma.ais.analysis.coverage.calculator.DistributeOnlyCalculator;
import dk.dma.ais.analysis.coverage.calculator.SatCalculator;
import dk.dma.ais.analysis.coverage.calculator.SupersourceCoverageCalculator;
import dk.dma.ais.analysis.coverage.configuration.AisCoverageConfiguration;
import dk.dma.ais.analysis.coverage.data.Cell;
//import dk.dma.ais.analysis.coverage.data.MongoBasedData;
import dk.dma.ais.analysis.coverage.data.OnlyMemoryData;
import dk.dma.ais.analysis.coverage.data.json.JSonCoverageMap;
import dk.dma.ais.analysis.coverage.data.json.JsonCell;
import dk.dma.ais.analysis.coverage.data.json.JsonConverter;
import dk.dma.ais.message.AisMessage;
import dk.dma.ais.packet.AisPacket;
import dk.dma.ais.packet.AisPacketTags.SourceType;
import dk.dma.ais.transform.SourceTypeSatTransformer;

/**
 * Handler for received AisPackets 
 */
public class CoverageHandler {

    private final AisCoverageConfiguration conf;
    private SupersourceCoverageCalculator superCalc;
    private DistributeOnlyCalculator distributeOnlyCalc;
    private SatCalculator satCalc;
    private int cellSize=2500;
    private static final Logger LOG = LoggerFactory.getLogger(CoverageHandler.class);
   
    public CoverageHandler(AisCoverageConfiguration conf) {
        this.conf = conf;
        
        superCalc = new SupersourceCoverageCalculator( false, conf.getSourceNameMap());
		superCalc.setCellSize(cellSize);	
		
		distributeOnlyCalc = new DistributeOnlyCalculator( false, conf.getSourceNameMap());
		distributeOnlyCalc.setCellSize(cellSize);	
		superCalc.addListener(distributeOnlyCalc);
		
		satCalc = new SatCalculator();
		satCalc.setCellSize(cellSize);
		
		
		//Setting data handlers
		if(conf.getDatabaseConfiguration().getType().toLowerCase().equals("memoryonly")){
			distributeOnlyCalc.setDataHandler(new OnlyMemoryData());
			superCalc.setDataHandler(new OnlyMemoryData());
			satCalc.setDataHandler(new OnlyMemoryData());	
			LOG.info("coverage calculators set up with memory only data handling");
		}else{
//			distributeOnlyCalc.setDataHandler(new MongoBasedData(conf.getDatabaseConfiguration()));
//			superCalc.setDataHandler(new MongoBasedData(conf.getDatabaseConfiguration()));
			LOG.info("coverage calculators set up with mongodb data handling");
		}
		
		
		//setting grid granularity
		distributeOnlyCalc.getDataHandler().setLatSize(conf.getLatSize());
		distributeOnlyCalc.getDataHandler().setLonSize(conf.getLonSize());
		superCalc.getDataHandler().setLatSize(conf.getLatSize());
		superCalc.getDataHandler().setLonSize(conf.getLonSize());
		satCalc.getDataHandler().setLatSize(conf.getLatSize());
		satCalc.getDataHandler().setLonSize(conf.getLonSize());
		LOG.info("grid granularity initiated with lat: "+conf.getLatSize() + " and lon: " + conf.getLonSize());
		
    }
    int pr=0;
    public void receiveUnfiltered(AisPacket packet) {

    	superCalc.processMessage(packet, "supersource");
    	distributeOnlyCalc.processMessage(packet, "1");    
        satCalc.processMessage(packet, "sat");

    }
    
    int filtCount = 0;
    public void receiveFiltered(AisPacket packet) {
        AisMessage message = packet.tryGetAisMessage();
        if (message == null) {
            return;
        }
        filtCount++;
//        superCalc.processMessage(message, "supersource");
    }
    
    public JSonCoverageMap getJsonCoverage(double latStart, double lonStart, double latEnd, double lonEnd, Map<String, Boolean> sources, int multiplicationFactor) {

		JSonCoverageMap map = new JSonCoverageMap();
		map.latSize=distributeOnlyCalc.getDataHandler().getLatSize()*multiplicationFactor;
		map.lonSize=distributeOnlyCalc.getDataHandler().getLonSize()*multiplicationFactor;
		

		HashMap<String, JsonCell> JsonCells = new HashMap<String, JsonCell>();

		List<Cell> celllist = distributeOnlyCalc.getDataHandler().getCells( latStart,  lonStart,  latEnd, lonEnd, sources, multiplicationFactor);
		HashMap<String,Boolean> superSourceIsHere = new HashMap<String,Boolean>();
		superSourceIsHere.put("supersource", true);
		List<Cell> celllistSuper = superCalc.getDataHandler().getCells( latStart,  lonStart,  latEnd, lonEnd, superSourceIsHere, multiplicationFactor);
		Map<String,Cell> superMap = new HashMap<String,Cell>();
		for (Cell cell : celllistSuper) {
			superMap.put(cell.getId(), cell);
		}
		
		if(!celllist.isEmpty())
			map.latSize = celllist.get(0).getGrid().getLatSize();
		
		for (Cell cell : celllist) {
			Cell superCell = superMap.get(cell.getId());
			if(superCell == null){

			}else{
				JsonCell existing = JsonCells.get(cell.getId());
				JsonCell theCell = JsonConverter.toJsonCell(cell, superCell);
				if (existing == null)
					existing = JsonCells.put(cell.getId(), JsonConverter.toJsonCell(cell, superCell));
				else if (theCell.getCoverage() > existing.getCoverage()){
					JsonCells.put(cell.getId(), theCell);
				}
			}
		}

		map.cells = JsonCells;
		return map;
	}
    
    
    public DistributeOnlyCalculator getDistributeCalc(){	return distributeOnlyCalc;	}
    public SupersourceCoverageCalculator getSupersourceCalc(){	return superCalc;	}
	public SatCalculator getSatCalc(){	return satCalc;	}
//

}
