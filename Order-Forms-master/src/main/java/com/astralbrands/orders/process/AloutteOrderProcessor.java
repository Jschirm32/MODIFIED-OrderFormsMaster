package com.astralbrands.orders.process;

import java.io.InputStream;
import java.util.*;

import org.apache.camel.Exchange;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.astralbrands.orders.constants.AppConstants;
import com.astralbrands.orders.dao.X3BPCustomerDao;
import org.springframework.stereotype.Component;

/*
	This Processor takes the input file and formats a text file containing
	only active orders in the input file. It formats the data in an 'IFILE' format for X3.
	It formats the same data but in a'.csv' file format for later processing.
 */
@Component
public class AloutteOrderProcessor implements BrandOrderForms, AppConstants {
	Logger log = LoggerFactory.getLogger(ReadXslxFileProcessor.class);
	
	@Autowired
	X3BPCustomerDao x3BPCustomerDao;

	// Map Object to hold the column names and their position in the Excel sheet
	static Map<String, Integer> colName = new HashMap<>();
	static {
		colName.put(PROD_DESC, 0);
		colName.put(MIN, 1);
		colName.put(STOCK_NUM, 2);
		colName.put(WHL, 3);
		colName.put(QTY_A, 4);
		colName.put(EXT_COST, 5);
		colName.put(REG_PROD, 6);
	}

	@Override
	public void process(Exchange exchange, String site, String[] fileNameData) {
		try {
			InputStream inputStream = exchange.getIn().getBody(InputStream.class);
			Workbook workbook = new XSSFWorkbook(inputStream); // Object to hold multiple Excel Sheets
			String headerStr = populateHeader(fileNameData,site);
			StringBuilder prodEntry = new StringBuilder();
			int numOfSheet = workbook.getNumberOfSheets(); // Holds the amount of Excel sheets to be processed
			log.info("Number of sheets we are processing : " + numOfSheet);
			// Loop to iterate through the Workbook object and process each sheet
			for (int i = 0; i < numOfSheet; i++) {
				Sheet firstSheet = workbook.getSheetAt(i); // Gets the next sheet

				readSheet(firstSheet, prodEntry, site); // Processes/Formats the data from the sheet
				log.info("FirstSheet : " + firstSheet);
			}
			String data = headerStr + NEW_LINE_STR + prodEntry.toString(); // Formats all the data into a String
			if (prodEntry.length() > 0) { // Begins processing the exchange data if the sheet is not empty
				exchange.getMessage().setBody(data);
				exchange.setProperty(CSV_DATA, data.replace(TILDE, COMMA)); // Formats the String for the CSV file by replacing the delimiter
				exchange.setProperty("IFILE", data);
				exchange.getMessage().setHeader(Exchange.FILE_NAME, exchange.getProperty(INPUT_FILE_NAME) + DOT_TXT); // Formats the name and data for the TXT file
				exchange.setProperty(IS_DATA_PRESENT, true);
				exchange.setProperty(SITE_NAME, site);
			} else {
				exchange.setProperty(IS_DATA_PRESENT, false);
			}
		} catch (Exception e) {
			e.printStackTrace();
			exchange.setProperty(IS_DATA_PRESENT, false);
		}
	}

	// Aloutte is based both in US & Canada - Function determines currency to use by comparing the file's site name
	private CharSequence getCurrency(String site) {
		if (US_STR.equals(site)) {
			return US_CURR;
		} else {
			return CA_CURR;
		}
	}

	/*
		This method iterates through every cell in each row of the current sheet
		For each row the cell's value for every column is added to
		a StringJoiner with a delimiter of '~' for X3 format
		-------Populates a StringBuilder with a product's info - If 'Qty' column is blank it is skipped-------
	 */
	private void readSheet(Sheet firstSheet, StringBuilder dataEntry, String site) {
		boolean entryStart = false;
		for (Row row : firstSheet) {
			ArrayList<Cell> cells = new ArrayList<>(); // Holds every cell's value
			Iterator<Cell> cellIterator = row.cellIterator(); // Iterates through every cell in the row
			cellIterator.forEachRemaining(cells::add); // Adds evey cell's value to the ArrayList
			// Objects to hold cell data using the Map object initialized at the top for column positions
			Cell prodDesc = row.getCell(colName.get(PROD_DESC)); // Product description column
			Cell quantity = row.getCell(colName.get(QTY_A)); // Quantity column
			Cell sku = row.getCell(colName.get(STOCK_NUM)); // Product's SKU - Stock # column
			StringJoiner entry = new StringJoiner(TILDE);

			// 'if' Statements to skip the first couple of rows - starts processing data after the column header row
			if (cells.size() == 0) {
				continue;
			}

			if (cells.size() < 2) {
				entryStart = false;
			}
			// Starts processing the sheet's data after it reaches the row with the column's names
			if (entryStart && cells.size() > 5) {
				Object qtyValue = getData(quantity); // Gets the value of the cell in the quantity column
				double qty = 0;
				if (qtyValue instanceof Double) {
					qty = (Double) qtyValue; // Converts that cell's value to a double
				}
				// Processes data from the sheet ONLY if the value in the quantity column is greater than 0
				if (qty > 0) {
					/*
					 * log.info(cells.size() + " qt " +qty + ", skuid :" +getData(cells.get(2)) +
					 * ", desc :" +getData(cells.get(0)) + ",site :" +
					 * getStockSite(getData(cells.get(cells.size()-1)), site));
					 */
//					String cost = getData(cells.get(3));
//					String a = cost.substring(1, cost.lastIndexOf("."));
//					String b = cost.substring((cost.lastIndexOf(".") + 1));
//					String TotalCost = getData(cells.get(5));
					entry.add(CHAR_L);
					entry.add(getData(sku)); // Product SKU
					entry.add(getData(prodDesc)); // Product description
					entry.add(getStockSite(getData(cells.get(cells.size()-1)), site)); // Site
					entry.add(EA_STR); // EA
					entry.add(((int) qty) + EMPTY_STR); // Quantity
//					entry.add(getData(cells.get(3))); // Price
//					entry.add(a + b);
					// entry.add("");// entry.add(getProdPrice(skuId, getValue(cells.get(3))));
					//'EMPTY_STR' 28 times for building the correct file format
					for (int i = 0; i < 28; i++) {
						entry.add(EMPTY_STR);
					}
					dataEntry.append(entry.toString()).append(NEW_LINE_STR);
				}
			}
			if (cells.size() > 5 && !entryStart) {
				String colName = cells.get(4).getStringCellValue();
				if (QUANTITY.equalsIgnoreCase(colName)) {
					entryStart = true;
				}
			}
		}
	}

	/*
		Function to take a cell (Excel spreadsheet cell) as a param
		Use 'switch' statement to determine the cell value's type
		Retrieves that value and returns it as a type Object
	 */
	private Object getValue(Cell cell) {
		Object value = null;
		FormulaEvaluator evaluator = null;
		switch (cell.getCellType()) {
			case Cell.CELL_TYPE_STRING:
				value = cell.getStringCellValue();
				break;
			case Cell.CELL_TYPE_NUMERIC:
				value = cell.getNumericCellValue();
				break;
//			case Cell.CELL_TYPE_FORMULA:
//				CellValue cellValue = evaluator.evaluate(cell);
//				if (cellValue != null) {
//					double val = cellValue.getNumberValue();
//					value = Math.round(val * 100.0) / 100.0;
//				}
//				break;
		default:
			break;
		}
		return value;
	}

	private String getStockSite(String flag, String site) {
		if (US_STR.equals(site)) {
			return "ALOUS";
		} else {
			if (flag != null && flag.trim().length() > 0 && flag.toLowerCase().equals("yes")) {
				return "ALCCA";
			}
			return "ALCUS";
		}
	}
	// Determines the site CA or US
	private String getSite(String site) {
		if (US_STR.equals(site)) {
			return "ALOUS";
		} else {
			return "ALCUS";
		}
	}

	// Populates the first row, header, in the csv/text file
	public String populateHeader(String[] fileNameData, String site) {
		StringJoiner header = new StringJoiner(TILDE);
		header.add(CHAR_E);
		header.add(getSite(site));
		// header.add("ALOUS");
		header.add(getOrderType(site));
		header.add(EMPTY_STR);
		header.add(fileNameData[0]);
		//header.add(EMPTY_STR); // extra line
		header.add(fileNameData[1]);
		header.add(fileNameData[1]);
		header.add(getSite(site));
		header.add(getCurrency(site));
		for (int i = 0; i < 26; i++) {
				header.add(EMPTY_STR);
		}
		header.add(x3BPCustomerDao.getPaymentTerms(fileNameData[0]));
		//header.add("NET30");
		return header.toString();
	}

	private String getOrderType(String site) {
		if (US_STR.equals(site)) {
			return "AUBLK";
		} else {
			return "ACBLK";
		}
	}

}
