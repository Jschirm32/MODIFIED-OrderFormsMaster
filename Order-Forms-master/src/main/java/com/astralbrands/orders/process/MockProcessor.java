package com.astralbrands.orders.process;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.camel.Exchange;
import org.apache.camel.builder.endpoint.dsl.LogEndpointBuilderFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.astralbrands.orders.constants.AppConstants;
import com.astralbrands.orders.dao.X3BPCustomerDao;



@Component
public class MockProcessor implements BrandOrderForms, AppConstants{

//    Logger log = LoggerFactory.getLogger(MockProcessor.class);

    @Autowired
    X3BPCustomerDao x3BPCustomerDao;

    /*
        Static declaration of a Map object to hold key/value pairs for
        the column's name in the Excel order form sheet, and it's position.

        -------In case Oder Form changes format - Easy to modify program-------
     */
    static Map<String, Integer> colName = new HashMap<>();
    static { // (Column name, Column position)
        colName.put(ITEM, 0);
        colName.put(ITEM_DESC, 1);
        colName.put(SIZE, 2);
        colName.put(ITEM_NAME, 3);
        colName.put(ITEM_COST, 4);
        colName.put(INT_COST, 5);
        colName.put(QTY, 6);
        colName.put(DIST_COST, 7);
        colName.put(TOTAL_COST, 8);
        colName.put(NAME, 9);
        colName.put(SHIPTO, 10);
        colName.put(CITY, 11);
        colName.put(STREET, 12);
        colName.put(ORDER_NUM, 13);
    }
//    static Map<Integer, Map<String, Integer>> colIndexMap = new HashMap<>();
//    static {


//        Map<String, Integer> secondSheet = new HashMap<>();
//        secondSheet.put(ITEM, 0);
//        secondSheet.put(TOTAL_COST, 2);
//        secondSheet.put(QTY, 3);
//        secondSheet.put(ORDER_NUM, 4);
//
//        Map<String, Integer> thirdSheet = new HashMap<>();
//        thirdSheet.put(ITEM, 0);
//        thirdSheet.put(TOTAL_COST, 2);
//        thirdSheet.put(QTY, 6);
//        thirdSheet.put(ORDER_NUM, 5);
//
//        colIndexMap.put(0, firstSheet);
//        colIndexMap.put(1, secondSheet);
//        colIndexMap.put(2, thirdSheet);
//    }

    @Override
    public void process(Exchange exchange, String site, String[] fileNameData) {
        try {
            InputStream inputStream = exchange.getIn().getBody(InputStream.class);
            Workbook workbook = new XSSFWorkbook(inputStream); // For future implementation of processing multiple sheets
            StringBuilder st = new StringBuilder();
//            int numOfSheets = workbook.getNumberOfSheets();
            Sheet sheet = workbook.getSheetAt(0); // For future implementation of processing multiple sheets

//            for (int i = 0; i < numOfSheets; i++) {
//
//            }
            String data = populateString(sheet, st);
            String today = currentDate();
            // Ensures there is data in the current Excel Sheet Order Form
            if(data != null) {
                exchange.setProperty(INPUT_FILE_NAME, "PURbeauty"); // CSV file
                exchange.getMessage().setBody(data); // Exchange data is set to the data processed in this class
                exchange.getMessage().setHeader(Exchange.FILE_NAME, today + "_PURbeauty" + DOT_TXT); // Formatting the TXT file
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
        Builds/Populates a String with the properly formatted info from processing
        the current Order form Excel Sheet. It uses two functions for obtaining
        product info line and the customer info line.
     */
    private String populateString(Sheet sheet, StringBuilder st){  // int pageIndex
        boolean skipHeader = true;

        String orderNum = EMPTY_STR; // Variable to hold the Order # column's value

        for(Row row : sheet) {
            ArrayList<Cell> cells = new ArrayList<>();
            Iterator<Cell> cellIterator = row.cellIterator();
            cellIterator.forEachRemaining(cells::add);
            Cell c1 = row.getCell(colName.get(ORDER_NUM)); // Gets the value for the current row's Order # column
            String OrderNum = getData(c1); // Formats that value into a String
            // Starts retrieving data after the first couple rows in the Sheet - skips unnecessary info
            if(cells.size() > 3) {
                if(skipHeader) {
                    skipHeader = false;
                }
                // If line has same Order # as another, add under the same customer header line
                else if (orderNum.equals(OrderNum)) {
                    st.append(getProdOrders(row));
                    st.append(NEW_LINE_STR);
                    orderNum = OrderNum;
                }
                // Obtains both the customer's info and product info on the current row
                else {
//                    Object qVal = getStringValue(cells.get(6));
//                    double qty = 0;
//                    if(qVal instanceof Double) {
//                        qty = (Double) qVal;
//                    }
//                    if(qty > 0) {
                        st.append(getProdCustInfo(row)); // Customer's info header line
                        st.append(NEW_LINE_STR);
                        st.append(getProdOrders(row)); // Customer's products ordered
                        st.append(NEW_LINE_STR);
                        orderNum = OrderNum;
//                    }
                }
            }
        }
        System.out.println("Current Test File is : " + st.toString());
        return st.toString();
    }

    // Simple function to format a cell's data value
    public String getData(Cell cell) {
        return new DataFormatter().formatCellValue(cell);
    }

    // Formats a cell's data value and returns it as a String
    private String getStringValue(Cell cell) {
        System.out.println("Value is : " + cell.toString());
        String value = getData(cell); // Formats the cell's value into a String
        if(value.toString().equalsIgnoreCase("N/A")) {
            return EMPTY_STR;
        }
        return value.toString();
    }

    // Retrieves the product's information for the current row in the Excel sheet Order form
    private String getProdOrders(Row row) {
        ArrayList<Cell> cells = new ArrayList<>(); // Holds every cell's value in the current row
        Iterator<Cell> cellIterator = row.cellIterator(); // Iterates through the entire row
        cellIterator.forEachRemaining(cells::add); // Adds each cell value to the ArrayList
//        Map<String, Integer> curSheet = colName;
        // Cell objects to hold a cell's value for the specified column in the current row
        Cell sku = row.getCell(colName.get(ITEM)); // Product's SKU #
        Cell desc = row.getCell(colName.get(ITEM_DESC)); // Product's description
        Cell qty = row.getCell(colName.get(QTY)); // Amount ordered
        Cell tCost = row.getCell(colName.get(TOTAL_COST)); // Total cost of the order
        StringJoiner lb = new StringJoiner("~");

        lb.add("L");
        lb.add(getData(sku)); // Product SKU #
        lb.add(getData(desc)); // Product Description
        lb.add("PURBE"); // Site
        lb.add(EA_STR); // Sales Unit
        lb.add(getData(qty)); // Quantity
        lb.add(getData(tCost)); // Total Price
        lb.add(ZERO); // Zero
        lb.add(EMPTY_STR); // Empty String
        lb.add(EMPTY_STR); // Empty String

        return lb.toString();
    }


    // Retrieves the customer's info from current row in the Excel sheet Order Form

    private String getProdCustInfo(Row row) {
        ArrayList<Cell> cells = new ArrayList<>();
        Iterator<Cell> cellIterator = row.cellIterator();
        cellIterator.forEachRemaining(cells::add);
        // Gets the cell's value in the given column for the current row
        Cell name = row.getCell(colName.get(NAME)); // Gets customer's name
        Cell street = row.getCell(colName.get(STREET)); // Gets customer's Street address
        Cell city = row.getCell(colName.get(CITY)); // Gets customer's city
        Cell shipTo = row.getCell(colName.get(SHIPTO)); // Gets customer's State and Country
        Cell orderNum = row.getCell(colName.get(ORDER_NUM)); // Gets the customer's Order #

        StringJoiner customerLine = new StringJoiner("~");
        customerLine.add("E");
        customerLine.add("PURBE"); // Sales Site
        customerLine.add("PURBD"); // Order Type
        customerLine.add(getData(orderNum)); // Order #
        customerLine.add(currentDate()); // Current Date
        customerLine.add("PURBE"); // Sales Site
        customerLine.add("USD"); // Currency
        for(int i = 0; i < 5; i++){
            customerLine.add(EMPTY_STR); // Blank spaces to separate data for x3 format
        }
        customerLine.add(getData(name)); // Customer's first name
        customerLine.add(getData(street)); // Customer's Street
        customerLine.add(getData(city)); // Customer's City
        customerLine.add(getData(shipTo)); // Customer's State/Country
        customerLine.add(ZERO);
        customerLine.add(EMPTY_STR);
        customerLine.add(EMPTY_STR);

        customerLine.add("NET90");

        return customerLine.toString();
    }
    // Obtains the current date and formats it
    private String currentDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd"); // Formats the date in the given format
        LocalDate date = LocalDate.now(); // Gets the current date when the program runs
//		LocalDate date = LocalDate.parse(today, formatter);
        String td = date.format(formatter); // Formats the current date and returns the value as a String
        return td;
    }

}
