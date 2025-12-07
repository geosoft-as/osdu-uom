package no.geosoft.uom;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.JsonReader;

/**
 * Units of measurement manager.
 * <p>
 * Singleton instance and main access point for quantities, units and
 * unit conversions.
 * <p>
 * This class is thread-safe.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class UnitManager
{
  /** The XML file with the Energistics unit database. */
  private final static String UNITS_FILE = "uom.json";

  /** Property file holding unit aliases. */
  private final static String UNIT_ALIASES_FILE = "unit_aliases.txt";

  /** The sole instance of this class. */
  private final static UnitManager instance_ = new UnitManager();

  /** Mapping between unit symbol alias and their equivalent "official" unit symbol. */
  private final Properties unitAliases_ = new Properties();

  /** Quantities known by this manager. */
  private final Collection<Quantity> quantities_ = new CopyOnWriteArraySet<>();

  /**
   * Return the sole instance of this class.
   *
   * @return  The sole instance of this class. Never null.
   */
  public static UnitManager getInstance()
  {
    return instance_;
  }

  /**
   * Create a unit manager instance.
   */
  private UnitManager()
  {
    loadFromJson();
    loadUnitAliases();
  }

  /**
   * Add specified alias to be associated with the given official
   * unit symbol.
   * <p>
   * The alias will be used when identifying Unit instances
   * from unit symbols and affects all methods of this class taking
   * unit symbol as argument.
   * <p>
   * Multiple aliases can be added for each unit symbol.
   *
   * @param unitSymbolAlias  Alias to add. Non-null.
   * @param unitSymbol       Unit symbol to associated alias with. Non-null.
   * @throws IllegalArgumentException  If unitSymbolAlias or unitSymbol is null.
   */
  public void addUnitAlias(String unitSymbolAlias, String unitSymbol)
  {
    if (unitSymbolAlias == null)
      throw new IllegalArgumentException("unitSymbolAlias cannot be null");

    if (unitSymbol == null)
      throw new IllegalArgumentException("unitSymbol cannot be null");

    unitAliases_.setProperty(unitSymbolAlias.toLowerCase(), unitSymbol);
  }

  /**
   * Return all quantities known by this unit manager.
   *
   * @return  All quantities. Never null.
   */
  public Collection<Quantity> getQuantities()
  {
    return Collections.unmodifiableCollection(quantities_);
  }

  /**
   * Add the specified quantity to this unit manager.
   *
   * @param quantity  Quantity to add. Non-null.
   * @throws IllegalArgumentException  If quantity is null or already contained
   *                  in this manager.
   */
  public void addQuantity(Quantity quantity)
  {
    if (quantity == null)
      throw new IllegalArgumentException("quantity cannot be null");

    if (findQuantity(quantity.getName()) != null)
      throw new IllegalArgumentException("Quantity is already present: " + quantity.getName());

    quantities_.add(quantity);
  }

  /**
   * Find quantity of the given name.
   *
   * @param quantityName  Name of quantity to find. Non-null.
   * @return              Requested quantity or null if not found.
   * @throws IllegalArgumentException  If quantityName is null.
   */
  public Quantity findQuantity(String quantityName)
  {
    if (quantityName == null)
      throw new IllegalArgumentException("quantityName cannot be null");

    for (Quantity quantity : quantities_) {
      if (quantity.getName().equals(quantityName))
        return quantity;
    }

    return  null;
  }

  /**
   * Return all units manageed by this manager.
   *
   * @return   All units. Never null.
   */
  public Collection<Unit> getUnits()
  {
    Map<String,Unit> units = new HashMap<>();

    // Capture all unique units
    for (Quantity quantity : quantities_) {
      for (Unit unit : quantity.getUnits()) {
        units.put(unit.getName(), unit);
      }
    }

    return units.values();
  }

  /**
   * Find corresponding unit instance for the given unit symbol.
   * <p>
   * The alias mapping is considered, and units are searched both case
   * sensitive and case insensitive.
   *
   * @param unitSymbol  Unit symbol to find unit for. May be null for unitless.
   * @return            Associated unit, or null if not found.
   */
  public Unit findUnit(String unitSymbol)
  {
    if (unitSymbol == null || unitSymbol.trim().isEmpty())
      unitSymbol = "unitless";

    String lowerCase = unitSymbol.toLowerCase(Locale.US).trim();

    // Check if there is an explicit mapping
    String actualUnitSymbol = unitAliases_.getProperty(lowerCase);
    if (actualUnitSymbol != null)
      unitSymbol = actualUnitSymbol;

    // Loop over all units to see if there is a matching one
    // with same case
    for (Quantity quantity : quantities_) {
      for (Unit unit : quantity.getUnits()) {
        String symbol = unit.getSymbol();
        if (unitSymbol.equals(symbol))
          return unit;
      }
    }

    // Do the same, but case insensitive this time
    for (Quantity quantity : quantities_) {
      for (Unit unit : quantity.getUnits()) {
        String symbol = unit.getSymbol();
        String symbolLowerCase = symbol.toLowerCase(Locale.US);
        if (lowerCase.equals(symbolLowerCase))
          return unit;
      }
    }

    // Not found
    return null;
  }

  /**
   * Convenience method for getting the display symbol of the specified
   * unit symbol.
   *
   * @param unitSymbol  Unit symbol to get display symbol of. May be null for unitless.
   * @return            Associated display symbol. Never null.
   */
  public String getDisplaySymbol(String unitSymbol)
  {
    Unit unit = findUnit(unitSymbol);
    return unit != null ? unit.getDisplaySymbol() : unitSymbol;
  }

  /**
   * Return all units that are convertible with the specified unit.
   *
   * @param unit  Unit to consider. Non-null.
   * @return      All convertible units. Never null.
   * @throws IllegalArgumentException  If unit is null.
   */
  public List<Unit> findConvertibleUnits(Unit unit)
  {
    if (unit == null)
      throw new IllegalArgumentException("unit cannot be null");

    Set<Unit> units = new HashSet<>();

    for (Quantity quantity : quantities_) {
      if (quantity.getUnits().contains(unit))
        units.addAll(quantity.getUnits());
    }

    units.remove(unit);

    return new ArrayList<>(units);
  }

  /**
   * Return all units that are convertible with the unit of the specified
   * symbol.
   *
   * @param unitSymbol  Symbol of unit to consider. Null if unitless.
   * @return            All convertible units. Never null.
   */
  public List<Unit> findConvertibleUnits(String unitSymbol)
  {
    Unit unit = findUnit(unitSymbol);
    return unit != null ? findConvertibleUnits(unit) : new ArrayList<>();
  }

  /**
   * Return all quantities that includes the specified unit.
   *
   * @param unit  Unit to consider. Non-null.
   * @return      Requested quantities. Never null.
   * @throws IllegalArgumentException  If unit is null.
   */
  public List<Quantity> findQuantities(Unit unit)
  {
    if (unit == null)
      throw new IllegalArgumentException("unit cannot be null");

    List<Quantity> quantities = new ArrayList<>();

    for (Quantity quantity : quantities_) {
      if (quantity.getUnits().contains(unit))
        quantities.add(quantity);
    }

    // If (one of) the quantities contains the Euclid unit (unitless)
    // we add the dimensionless quantity as well.
    Unit euclidUnit = findUnit("Euc");
    for (Quantity quantity : quantities) {
      if (quantity.getUnits().contains(euclidUnit)) {
        quantities.add(findQuantity("dimensionless"));
        break;
      }
    }

    return quantities;
  }

  /**
   * Return all quantities that includes the unit of the specified symbol.
   *
   * @param unitSymbol  Unit symbol of unit to consider. Null if unitless.
   * @return            Requested quantities. Never null.
   */
  public List<Quantity> findQuantities(String unitSymbol)
  {
    Unit unit = findUnit(unitSymbol);
    return unit != null ? findQuantities(unit) : new ArrayList<>();
  }

  /**
   * Find quantity of the specified unit.
   * <p>
   * Note that a unit may be contained in multiple quantities.
   * This method is convenient if the client knows that the unit
   * exists in one quantity only. If it exists in more than one
   * quantity, the first one encountered is returned.
   *
   * @param unit  Unit to consider. Non-null.
   * @return      Requested quantity or null if none found.
   * @throws IllegalArgumentException  If unit is null.
   */
  public Quantity findQuantity(Unit unit)
  {
    if (unit == null)
      throw new IllegalArgumentException("unit cannot be null");

    List<Quantity> quantities = findQuantities(unit);

    if (quantities.isEmpty())
      return null;

    else if (quantities.size() == 1)
      return quantities.get(0);

    else {
      // Unit clash: "Siemens (S) - seconds (s)"
      if (quantities.contains(findQuantity("time")))
        return findQuantity("time");

      // TODO: Others

      return quantities.get(0);
    }
  }

  /**
   * Check if it is possible to convert between the two specified units.
   *
   * @param unit1  First unit to consider. Non-null.
   * @param unit2  Second unit to consider. Non-null.
   * @return       True if it is possible to convert between the two,
   *               false otherwise.
   * @throws IllegalArgumentException  If unit1 or unit2 is null.
   */
  public boolean canConvert(Unit unit1, Unit unit2)
  {
    if (unit1 == null)
      throw new IllegalArgumentException("unit1 cannot be null");

    if (unit2 == null)
      throw new IllegalArgumentException("unit2 cannot be null");

    List<Quantity> quantities1 = findQuantities(unit1);
    List<Quantity> quantities2 = findQuantities(unit2);

    for (Quantity quantity : quantities1)
      if (quantities2.contains(quantity))
        return true;

    return false;
  }

  /**
   * Check if it is possible to convert between the two specified units.
   *
   * @param unitSymbol1  Unit symbol of first unit to consider. Null if unitless.
   * @param unitSymbol2  Unit symbol of second unit to consider. Null if unitless.
   * @return             True if it is possible to convert between the two,
   *                     false otherwise.
   */
  public boolean canConvert(String unitSymbol1, String unitSymbol2)
  {
    if (Objects.equals(unitSymbol1, unitSymbol2))
      return true;

    Unit unit1 = findUnit(unitSymbol1);
    Unit unit2 = findUnit(unitSymbol2);

    return unit1 != null && unit2 != null ? canConvert(unit1, unit2) : false;
  }

  /**
   * Convert the specified value between the two given units.
   * <p>
   * Note that it is the client responsibility to check if it makes sense to
   * convert between the given units. This method simply converts the value
   * to base of the from unit, and convert this result from base of the to unit,
   * without considering the compatibility between the two.
   *
   * @param fromUnit  Current unit of value. Non-null.
   * @param toUnit    Unit to convert to. Non-null.
   * @param value     Value to convert.
   * @return          Converted value.
   * @throws IllegalArgumentException  If fromUnit or toUnit is null.
   */
  public static double convert(Unit fromUnit, Unit toUnit, double value)
  {
    if (fromUnit == null)
      throw new IllegalArgumentException("fromUnit cannot be null");

    if (toUnit == null)
      throw new IllegalArgumentException("toUnit cannot be null");

    double baseValue = fromUnit.toBase(value);
    return toUnit.fromBase(baseValue);
  }

  /**
   * Convert the specified value between the two given units.
   * <p>
   * Note that it is the client responsibility to check if it makes sense to
   * convert between the given units. This method simply converts the value
   * to base of the from unit, and convert this result from base of the to unit,
   * without considering the compatibility between the two.
   *
   * @param fromUnitSymbol  Unit symbol of current unit of value. Non-null.
   * @param toUnitSymbol    Unit symbol of unit to convert to. Non-null.
   * @param value           Value to convert.
   * @return                Converted value, or the input value it unit symbols
   *                        are unknown.
   * @throws IllegalArgumentException  If fromUnitSymbol or toUnitSymbol is null.
   */
  public double convert(String fromUnitSymbol, String toUnitSymbol, double value)
  {
    if (fromUnitSymbol == null)
      throw new IllegalArgumentException("fromUnitSymbol cannot be null");

    if (toUnitSymbol == null)
      throw new IllegalArgumentException("toUnitSymbol cannot be null");

    Unit fromUnit = findUnit(fromUnitSymbol);
    Unit toUnit = findUnit(toUnitSymbol);

    return fromUnit != null && toUnit != null ? convert(fromUnit, toUnit, value) : value;
  }

  /**
   * Load all unit aliases from local properties file.
   */
  private void loadUnitAliases()
  {
    InputStream stream = null;

    try {
      stream = UnitManager.class.getResourceAsStream(UNIT_ALIASES_FILE);
      unitAliases_.load(stream);
    }
    catch (IOException exception) {
      // Ignore. If the file is not available we can run without
    }
    finally {
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException exception) {
          // Ignore.
        }
      }
    }
  }

  /**
   * Find the requested unit among the set.
   *
   * @param units  Units to search. Non-null.
   * @param name   Name of unit to find. Non-null.
   * @return       The requested unit, or null if not found.
   */
  private static Unit findUnitByName(Set<Unit> units, String name)
  {
    assert units != null : "units cannot be null";
    assert name != null : "name cannot be null";

    for (Unit unit : units) {
      if (unit.getName().equals(name))
        return unit;
    }

    // Not found
    return null;
  }

  /**
   * Load quantities and units from the standard.
   */
  private void loadFromJson()
  {
    Set<Unit> units = new HashSet<>();

    try {
      // Read the Energistics UoM standard
      InputStream stream = UnitManager.class.getResourceAsStream(UNITS_FILE);

      JsonReader reader = Json.createReader(stream);
      JsonObject energisticsStandard = reader.readObject();

      // Load all units
      JsonArray unitObjects = energisticsStandard.getJsonArray("units");
      for (JsonObject unitObject : unitObjects.getValuesAs(JsonObject.class)) {
        String name = unitObject.getString("name");
        String symbol = unitObject.getString("symbol");
        String displaySymbol = unitObject.getString("displaySymbol");

        // NOTE: We have a different definition of a, b, c, d than Energistics
        //       so the switch of order is intentional
        double a = unitObject.getJsonNumber("a").doubleValue();
        double b = unitObject.getJsonNumber("b").doubleValue();
        double c = unitObject.getJsonNumber("c").doubleValue();
        double d = unitObject.getJsonNumber("d").doubleValue();
        Unit unit = new Unit(name, symbol, a, b, c, d, displaySymbol);
        units.add(unit);
      }

      // Load all quantities
      JsonArray quantityObjects = energisticsStandard.getJsonArray("quantities");
      for (JsonObject quantityObject : quantityObjects.getValuesAs(JsonObject.class)) {
        String name = quantityObject.getString("name");
        Quantity quantity = new Quantity(name);
        quantities_.add(quantity);

        JsonArray memberUnits = quantityObject.getJsonArray("units");
        for (JsonString unitName : memberUnits.getValuesAs(JsonString.class)) {
          Unit unit = findUnitByName(units, unitName.getString());
          quantity.addUnit(unit, false); // The first one will be base unit
        }
      }
    }
    catch (Exception exception) {
      exception.printStackTrace();
      assert false : "This will not happen";
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    StringBuilder s = new StringBuilder();

    s.append("Quantities....: " + quantities_.size() + "\n");
    int nUnits = 0;
    for (Quantity quantity : quantities_)
      nUnits += quantity.getUnits().size();
    s.append("Units.........: " + nUnits + "\n");
    s.append("Unit aliases..: " + unitAliases_.size());

    return s.toString();
  }

  /**
   * Testing this class.
   *
   * @param arguments  Application argumenst. Not used.
   */
  private static void main(String[] arguments)
  {
    Unit unit = UnitManager.getInstance().findUnit("ohm.m");
    List<Quantity> quantities = UnitManager.getInstance().findQuantities(unit);
    for (Quantity q : quantities)
      System.out.println(q);

    Quantity q = UnitManager.getInstance().findQuantity("length");
    for (Unit u : q.getUnits()) {
      System.out.println(u + ": 10.0 = " + u.toBase(10.0));
    }
  }
}
