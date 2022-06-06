package com.astralbrands.orders.process;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.camel.Exchange;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.astralbrands.orders.constants.AppConstants;
import com.astralbrands.orders.dao.X3BPCustomerDao;

/*
	This Processor takes the input file and formats
	a text file containing orders from the input file. It formats the data
	in an 'IFILE' format in a TXT file for X3. It stores the same data to
	later be processed into a new '.csv' file
 */
@Component
public class CommerceHubProcessor implements BrandOrderForms, AppConstants {
	
	Logger log = LoggerFactory.getLogger(CommerceHubProcessor.class);

	@Autowired
	X3BPCustomerDao x3BPCustomerDao;

	// Map Object to hold every column name, and it's position in the Order Form
	static Map<String, Integer> colName = new HashMap<>();
	static {
		colName.put(INS_DATE, 0); // Not used
		colName.put(ORDER_DATE, 1);
		colName.put(STATUS, 2); // Not used
		colName.put(PO_NUM, 3);
		colName.put(CUST_ORD_NUM, 4);
		colName.put(BILL_NAME, 5);
		colName.put(BILL_ADD, 6);
		colName.put(BILL_ADD2, 7);
		colName.put(BILL_CITY, 8);
		colName.put(BILL_ST, 9);
		colName.put(BILL_ZIP, 10);
		colName.put(BILL_COUNTRY, 11);
		colName.put(SHIP_NAME, 12);
		colName.put(SHIP_ADD, 13);
		colName.put(SHIP_ADD2, 14);
		colName.put(SHIP_CITY, 15);
		colName.put(SHIP_STATE, 16);
		colName.put(SHIP_ZIP, 17);
		colName.put(SHIP_COUNTRY, 18);
		colName.put(TAX, 19); // Not used
		colName.put(TAX_CURRENCY, 20); // Not used
		colName.put(VEN_SKU, 21);
		colName.put(DESCRIPTION, 22);
		colName.put(QTY_HUB, 23);
		colName.put(UNIT_COST, 24);
	}
	
	@Override
	public void process(Exchange exchange, String site, String[] fileNameData) {
		try {
			InputStream inputStream = exchange.getIn().getBody(InputStream.class);
			Workbook workbook = new XSSFWorkbook(inputStream); // Object to hold multiple sheets for processing
			StringBuilder txtFileBuilder = new StringBuilder();
			Sheet firstSheet = workbook.getSheetAt(0); // Gets the first sheet from the Workbook Object
			String txtFileData = populateTxtString(firstSheet,txtFileBuilder); // Processes the sheet and formats the data for the new TXT & CSV files
			String today = currentDate(); // Returns the current date when this program runs
			System.out.println("Output data is : "+ txtFileData);

			if (txtFileData != null) { // Ensures the current sheet is valid/contains data
				exchange.setProperty(INPUT_FILE_NAME, today + "_CommerceHub"); //Removed '.txt' for the '.csv' output file name
				exchange.getMessage().setBody(txtFileData); // Sets the exchange value as the new formatted data in this Processor class
				exchange.getMessage().setHeader(Exchange.FILE_NAME,  today + "_CommerceHub" + DOT_TXT); //Formats '.txt' file with today's date
				exchange.setProperty(IS_DATA_PRESENT, true);
			} else {
				exchange.setProperty(IS_DATA_PRESENT, false);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	/*
		Function to build the structure for the files || Calls two local functions
		to populate the StringBuilder with the sheet's cell values in a specific format
		for X3.
 ----------Builds/Populates a String formatted with all header lines & product info lines from the sheet----------
	 */
	private String populateTxtString(Sheet firstSheet, StringBuilder txtFileBuilder) {

		boolean skipHeader = true;
	
		String tmpPO = EMPTY_STR; // Order #
		System.out.println("String builder is : "+ txtFileBuilder.toString());
		System.out.println("Number of cells are : "+firstSheet);
		
		for(Row row : firstSheet) {
			ArrayList<Cell> cells = new ArrayList<>();
			Iterator<Cell> cellIterator = row.cellIterator();
			cellIterator.forEachRemaining(cells::add);
			Cell poNum = row.getCell(colName.get(PO_NUM));
			//cells.size();
			System.out.println(cells.size());
			if(cells.size()>3) { // Starts after the row with column names
				if(skipHeader) {
					skipHeader=false;
				}
				// If a product has the same Order# then add product info under same customer line
				else if(tmpPO.equals(getData(poNum))){
					txtFileBuilder.append(getOrderLine(row));
					//System.out.println("String builder is : "+ txtFileBuilder.toString());
					txtFileBuilder.append(NEW_LINE_STR);
					tmpPO = getData(poNum); // Order #
					System.out.println("tmpPo number is : "+ tmpPO);
				}
				// Builds the formatted data from the Excel sheet in a String
				else {
					System.out.println("row value is "+row.getCell(2));
					txtFileBuilder.append(getHeader(row)); // Builds the customer info line
					//System.out.println("String builder is : "+ txtFileBuilder.toString()); //Duplicate line
					txtFileBuilder.append(NEW_LINE_STR);
					txtFileBuilder.append(getOrderLine(row)); // Builds the corresponding product info line
					System.out.println("String builder is : "+ txtFileBuilder.toString());
					txtFileBuilder.append(NEW_LINE_STR);
					tmpPO = getData(poNum); // Order #
					System.out.println("tmpPo number is : "+ tmpPO);
				}
			}
		}
		System.out.println("Text file is : "+txtFileBuilder.toString());
		return txtFileBuilder.toString();
	}

	/*
		Iterates through cells pertaining to Product information in the given row and adds the values to an ArrayList
		Returns a formatted String separated by '~' after each value obtained from each cell
		in the current row of the Excel Sheet
		----------Builds the product info line in the TXT file-----------
	 */
	private String getOrderLine(Row row) {
		
		ArrayList<Cell> cells = new ArrayList<>();
		Iterator<Cell> cellIterator = row.cellIterator();
		cellIterator.forEachRemaining(cells::add);
		// Cell objects to hold the current row's cell values
		Cell sku = row.getCell(colName.get(VEN_SKU));
		Cell description = row.getCell(colName.get(DESCRIPTION));
		Cell qty = row.getCell(colName.get(QTY_HUB));
		Cell price = row.getCell(colName.get(UNIT_COST));
//		System.out.println("row is : "+cellIterator); // - Unnecessary - Program takes longer with this line
 		StringJoiner lineBuilder = new StringJoiner("~");
		System.out.println("Line builder is : "+ lineBuilder.toString() + "\n");
		lineBuilder.add("L");
		lineBuilder.add(getData(sku)); //SKU
		lineBuilder.add(getData(description)); //Description
		lineBuilder.add("BUTCO"); //site
		lineBuilder.add("EA"); //Sales Unit
		lineBuilder.add(getData(qty)); //Quantity
		lineBuilder.add(getData(price)); //Gross Price
//		lineBuilder.add("0" + getValue(cells.get(24)).replace(".", "") + "0"); //Gross price - Formatting the price section with no '.' and a '0' at the beginning and end
		lineBuilder.add(ZERO);
		lineBuilder.add(EMPTY_STR);
		return lineBuilder.toString();
	}

	// Formats a cell's data value within a row/column of an Excel sheet
	public String getData(Cell cell) {
		return new DataFormatter().formatCellValue(cell);
	}

	// Converts a Cell object value to a String
	private String getValue(Cell cell) {
		System.out.println("Value is : "+cell.toString());
		String value = getData(cell);
//		System.out.println("Value is : "+value);
		if(value.toString().equalsIgnoreCase("N/A")) {
			return EMPTY_STR;
		}
		return value.toString();
	}

	/*
		Obtains the customer's information along with the order number, Shipping site,
		Sales Site, Order Type, Customer order reference, Date, and currency.
		Formatted in a specific order for X3
		----------Builds each header line in the TXT file-----------
	 */
	private String getHeader(Row row) {
		
		ArrayList<Cell> cells = new ArrayList<>();
		Iterator<Cell> cellIterator = row.cellIterator();
		cellIterator.forEachRemaining(cells::add);

	//---------CELL OBJECTS TO HOLD POSITION FOR COLUMN NAMES---------

		Cell poNum = row.getCell(colName.get(PO_NUM));
		Cell orderDate = row.getCell(colName.get(ORDER_DATE));
		Cell custOrdNum = row.getCell(colName.get(CUST_ORD_NUM));
		Cell billName = row.getCell(colName.get(BILL_NAME));
		Cell country = row.getCell(colName.get(BILL_COUNTRY));
		Cell billAdd = row.getCell(colName.get(BILL_ADD));
		Cell billAdd2 = row.getCell(colName.get(BILL_ADD2));
		Cell zip = row.getCell(colName.get(BILL_ZIP));
		Cell billCity = row.getCell(colName.get(BILL_CITY));
		Cell billState = row.getCell(colName.get(BILL_ST));
		Cell shipName = row.getCell(colName.get(SHIP_NAME));
		Cell shipCountry = row.getCell(colName.get(SHIP_COUNTRY));
		Cell shipAdd = row.getCell(colName.get(SHIP_ADD));
		Cell shipAdd2 = row.getCell(colName.get(SHIP_ADD2));
		Cell shipZip = row.getCell(colName.get(SHIP_ZIP));
		Cell shipCity = row.getCell(colName.get(SHIP_CITY));
		Cell shipState = row.getCell(colName.get(SHIP_STATE));
		
		StringJoiner headerBuilder = new StringJoiner("~");
		System.out.println("headerBuilder is : "+ headerBuilder.toString());
		//System.out.println(Header is : "+row);
		headerBuilder.add("E");
		headerBuilder.add("BUTCO"); //Sales Site/SALFCY
		headerBuilder.add("BQVCD"); //Order Type/SOHTYP
		headerBuilder.add(getData(poNum)); //PO number
		headerBuilder.add("460000147"); //BPCORD
		headerBuilder.add(getDate(getData(orderDate))); //Date
		headerBuilder.add(getData(custOrdNum)); //Customer order reference
		headerBuilder.add("BUTCO"); // Shipping site
		headerBuilder.add("USD"); //Currency
		for(int i=0; i<5; i++) {
			headerBuilder.add(EMPTY_STR);
		}
		headerBuilder.add(getData(billName)); //Bill firstName
		headerBuilder.add(EMPTY_STR); //Bill lastName 
		headerBuilder.add(getData(country)); //Bill country
		headerBuilder.add(getData(billAdd)); //Bill Add 1
		headerBuilder.add(getData(billAdd2)); //Bill Add 2
		headerBuilder.add(getData(zip)); //Bill postal code
		headerBuilder.add(getData(billCity)); // Bill city
		headerBuilder.add(getData(billState)); //Bill state
		headerBuilder.add(getData(shipName)); //Ship firstname
		headerBuilder.add(EMPTY_STR); //Ship LastName
		headerBuilder.add(getData(shipCountry)); //Ship country
		headerBuilder.add(getData(shipAdd)); //Ship Add 1
		headerBuilder.add(getData(shipAdd2)); //Ship Add 2
		headerBuilder.add(getData(shipZip)); //Ship Postal code
		headerBuilder.add(getData(shipCity)); //Ship city
		headerBuilder.add(getData(shipState)); //Ship State
		headerBuilder.add(ZERO);
		headerBuilder.add(ZERO);
//		headerBuilder.add(getValue(cells.get(19))); // Tax
		headerBuilder.add(EMPTY_STR);
		headerBuilder.add(EMPTY_STR);
		headerBuilder.add(EMPTY_STR);
		headerBuilder.add("NET90");
		return headerBuilder.toString();
	}

	private String getDate(String date) {
		System.out.println("Date is : "+date);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yy");
	    LocalDate ld = LocalDate.parse(date,formatter);
		//System.out.println("Date is : "+ld.getMonthValue()+" "+ld.getDayOfMonth()+" "+ld.getYear());
		return ld.getYear()+""+(ld.getMonthValue()<10?("0"+ld.getMonthValue()):ld.getMonthValue())+""+(ld.getDayOfMonth()<10?("0"+ld.getDayOfMonth()):ld.getDayOfMonth());
	}
	private String currentDate() {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		LocalDate date = LocalDate.now();
//		LocalDate date = LocalDate.parse(today, formatter);
		String td = date.format(formatter);
		return td;
	}

}
