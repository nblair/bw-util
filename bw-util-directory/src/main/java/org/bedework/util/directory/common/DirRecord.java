package org.bedework.util.directory.common;

import java.io.Serializable;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.ModificationItem;

/** This class represents a directory record which may be built from a
    number of attributes, retrieved as a search result or read from some input
    stream.

    <p>We will implement some aspects of the ldif as defined in RFC 2849
    within this class. These will be related to the content vs change records.
    By default, a DirRecord object will be a content record. However, we will
    allow the creation of change type records. Having this defined here allows
    applications to pass around DirRecord objects without any idea of the
    underlying implementation.

    <p>Currently the following is implemented:<ul>
    <li><em>content records</em>  Consist of a dn with an associated set of
          attributes with
          values. No attribute options are implemented. Encoded strings may be
          converted incorrectly (need to check locale specifics)</li>
     <li><em>change records.</em><br/> <ul>
        <li><em>add:</em> Implemented. Look just like content records.</li>
        <li><em>delete:</em> Implemented. Consist of a dn only.</li>
        <li>all others are unimplemented.</li></ul>
     </li></ul>

  */
public abstract class DirRecord implements Serializable {
  /** @serial */
  private String dn;

  /** @serial */
  private boolean isContentRec = true;

  /** @serial */
  private int changeType = changeTypeInvalid;

  public static final int changeTypeInvalid = 0;
  public static final int changeTypeAdd = 1;
  public static final int changeTypeDelete = 2;
  public static final int changeTypeModify = 3;
  public static final int changeTypeModdn = 4;

  public abstract Attributes getAttributes() throws NamingException;

  /** Find the attribute for this record with the given name.
      Return null if not defind.
   */
  public Attribute findAttr(String attr) throws NamingException {
    return getAttributes().get(attr);
  }

  /** Set the attribute value in the table. Replaces any existing value(s)
      This does not write back to the directory
   */
  public void setAttr(String attr, Object val) throws NamingException {
    getAttributes().put(attr, val);
  }

  /** Should be overriden to clear attributes.
   */
  public void clear() {
    dn = null;
    isContentRec = true;
    changeType = changeTypeInvalid;
  }

  /** return the dn for this record.
   */
  public String getDn() throws Exception {
    return dn;
  }

  /** Set the dn for this record.
   */
  public void setDn(String dn) throws NamingException {
    this.dn = dn;
  }

  /** Compare this with that. Return true if they are equal.
   */
  public boolean equals(DirRecord that) throws Exception {
    if (!dnEquals(that)) {
      return false;
    }

    Attributes thisAttrs = getAttributes();
    Attributes thatAttrs = that.getAttributes();

    if (thisAttrs == null) {
      if (thatAttrs == null) {
        return true;
      }
      return false;
    }

    if (thatAttrs == null) {
      return false;
    }

    return thisAttrs.equals(thatAttrs);
  }

  /** Compare this with that only looking at the named attributes.
      For this method, the dns must be equal and the values of the named
      attributes must be equal but their names may differ. The two arrays of
      attrIDs must be non-null and of the same length or an exception is raised.

      If there are no attributes in both records they are considered equal.
      If there are no attributes in only one record they are unequal.

      Zero length attrID lists means only the dn is compared.
   */
  public boolean equals(DirRecord that, String[] thisAttrIDs,
     String[] thatAttrIDs) throws Exception {
    if ((thisAttrIDs == null) || (thatAttrIDs == null)) {
      throw new Exception("DirectoryRecord: null attrID list");
    }

    if (thisAttrIDs.length != thatAttrIDs.length) {
      throw new Exception("DirectoryRecord: unequal length attrID lists");
    }

    if (!dnEquals(that)) {
      return false;
    }

    int n = thisAttrIDs.length;

    if (n == 0) {
      return true;
    }

    Attributes thisAttrs = getAttributes();
    Attributes thatAttrs = that.getAttributes();

    if (thisAttrs == null) {
      if (thatAttrs == null) {
        return true;
      }
      return false;
    }

    if (thatAttrs == null) {
      return false;
    }

    for (int i = 0; i < n; i++) {
      Attribute thisAttr = thisAttrs.get(thisAttrIDs[i]);
      Attribute thatAttr = thatAttrs.get(thatAttrIDs[i]);

      if (thisAttr == null) {
        if (thatAttr == null) {
          return true;
        }
        return false;
      }

      if (thatAttr == null) {
        return false;
      }

      if (!attrEquals(thisAttr, thatAttr)) {
        return false;
      }
    }

    return true;
  }

  /** Simpler form of equals in which attributes have the same names in both
      records.
   */
  public boolean equals(DirRecord that, String[] attrIDs) throws Exception {
    return equals(that, attrIDs, attrIDs);
  }

  /** This compares all but the named attributes
      allbut true => All must be equal except those on the list
   */
  public boolean equalsAllBut(DirRecord that, String[] attrIDs) throws Exception {
    if (attrIDs == null)
      throw new Exception("DirectoryRecord: null attrID list");

    if (!dnEquals(that)) {
      return false;
    }

    int n = attrIDs.length;

    if (n == 0) return true;

    Attributes thisAttrs = getAttributes();
    Attributes thatAttrs = that.getAttributes();

    if (thisAttrs == null) {
      if (thatAttrs == null) return true;
      return false;
    }

    if (thatAttrs == null) {
      return false;
    }

    /** We need to ensure that all attributes are checked.
        We init thatLeft to the number of attributes in the source.
        We decrement for each checked attribute.
        We then decrement for each ignored attribute present in that
        If the result is non-zero, then there are some extra attributes in that
        so we return unequal.
      */
    int sz = thisAttrs.size();
    int thatLeft = sz;

    if ((sz == 0) && (thatAttrs.size() == 0)) {
      return true;
    }

    NamingEnumeration ne = thisAttrs.getAll();

    if (ne == null) {
      return false;
    }

    while (ne.hasMore()) {
      Attribute attr = (Attribute)ne.next();
      String id = attr.getID();
      boolean present = false;

      for (int i = 0; i < attrIDs.length; i++) {
        if (id.equalsIgnoreCase(attrIDs[i])) {
          present = true;
          break;
        }
      }
      if (present) {
        // We don't compare
        if (thatAttrs.get(id) != null) thatLeft--;
      } else {
        Attribute thatAttr = thatAttrs.get(id);
        if (thatAttr == null) {
          return false;
        }
        if (!thatAttr.contains(attr)) {
          return false;
        }
        thatLeft--;
      }
    }

    return (thatLeft == 0);
  }

  public boolean attrEquals(Attribute thisA, Attribute that) throws Exception {
    int sz = thisA.size();

    if (sz != that.size()) {
      return false;
    }

    if (sz == 0) {
      return true;
    }

    NamingEnumeration ne = thisA.getAll();

    if (ne == null) {
      return false;
    }

    while (ne.hasMore()) {
      if (!that.contains(ne.next())) {
        return false;
      }
    }

    return true;
  }

  /** Compare the given single value with the attribute value(s).
   *  @return -2 for not equal or not present in multi-valued attribute
   *          -1 for val &lt; that
   *           0 for val = that
   *           1 for val &gt; that
   *           2 for val present in multi-valued attr
   */
  public int attrValCompare(Object val, Attribute that,
                            boolean ignoreCase) throws Exception {
    if (that.size() != 1) {
      NamingEnumeration ne = that.getAll();

      if (ne == null) {
        return -2;
      }

      while (ne.hasMore()) {
        Object o = ne.next();
        if (val instanceof String) {
          if (compareVal(o, (String)val, ignoreCase) == 0) {
            return 2;
          }
        } else if (o.equals(val)) {
          return 2;
        }
      }
      return -2;
    }

    /** that is a single valued attribute.
     */
    Object o = that.get();

    if (val instanceof String) {
      return compareVal(o, (String)val, ignoreCase);
    }

    if (o.equals(val)) {
      return 0;
    }

    return -2;
  }

  /** Extract the target attribute from this record then
   *  compare the given single value with the attribute value(s).
   *  @return -2 for not equal or not present in multi-valued attribute
   *          -1 for val &lt; that
   *           0 for val = that
   *           1 for val &gt; that
   *           2 for val present in multi-valued attr
   */
  public int attrValCompare(Object val, String attrName,
                            boolean ignoreCase) throws Exception {
    Attribute a = findAttr(attrName);

    if (a == null) {
      return -2;
    }
    return attrValCompare(val, a, ignoreCase);
  }

  private int compareVal(Object o, String s, boolean ignoreCase) {
    if (!(o instanceof String)) return -2;

    int c;
    if (ignoreCase) {
      c = s.compareToIgnoreCase((String) o);
    } else {
      c = s.compareTo((String) o);
    }

    if (c < 0) {
      c = -1;
    }
    if (c > 0) {
      c = 1;
    }

    return c;
  }

  /** Check dns for equality
   */
  public boolean dnEquals(DirRecord that) throws Exception {
    if (that == null) {
      throw new Exception("Null record for dnEquals");
    }

    String thisDn = getDn();
    if (thisDn == null) {
      throw new Exception("No dn for this record");
    }

    String thatDn = that.getDn();
    if (thatDn == null) {
      throw new Exception("That record has no dn");
    }

    return (thisDn.equals(thatDn));
  }

  /** Add the attribute value to the table. If an attribute already exists
   *  add it to the end of its values.
   *
   * @param   attr   String attribute name
   * @param   val    Object value
   */
  public void addAttr(String attr, Object val) throws NamingException {
//  System.out.println("addAttr " + attr);

    Attribute a = findAttr(attr);

    if (a == null) {
      setAttr(attr, val);
    } else {
      a.add(val);
    }
  }

  /** getAttrVal - return first (or only) value for given attribute
   *  "dn" is treated as an attribute name.
   *
   * @param   attr   String attribute name
   * @return  Object attribute value
   */
  public Object getAttrVal(String attr) throws Exception {
    if (attr.equalsIgnoreCase("dn")) {
      return getDn();
    }

    Attribute a = findAttr(attr);

    if (a == null) {
      return null;
    }

    return a.get();
  }

  /** Return true if the record contains all of the values of the given
   *  attribute.
   *
   * @param   attr    Attribute we're looking for
   * @return  boolean true if we found it
   */
  public boolean contains(Attribute attr) throws Exception {
    if (attr == null) {
      return false; // protect
    }

    Attribute recAttr = getAttributes().get(attr.getID());

    if (recAttr == null) {
      return false;
    }

    NamingEnumeration ne = attr.getAll();

    while (ne.hasMore()) {
      if (!recAttr.contains(ne.next())) {
        return false;
      }
    }

    return true;
  }

   /**
     * Retrieve an enumeration of the named attribute's values.
     * The behaviour of this enumeration is unspecified
     * if the the attribute's values are added, changed,
     * or removed while the enumeration is in progress.
     * If the attribute values are ordered, the enumeration's items
     * will be ordered.
     *
     * @return A non-null enumeration of the attribute's values.
     * Each element of the enumeration is a possibly null Object. The object's
     * class is the class of the attribute value. The element is null
     * if the attribute's value is null.
     * If the attribute has zero values, an empty enumeration
     * is returned.
     * @exception javax.naming.NamingException
     *		If a naming exception was encountered while retrieving
     *		the values.
     */
  public NamingEnumeration attrElements(String attr) throws NamingException {
    Attribute a = findAttr(attr);

    if (a == null) {
      return null;
    }

    return a.getAll();
  }

  /** getAttrStr - return first (or only) string value for given attribute
   *
   * @param   attr   String attribute name
   * @return  String attribute value
   */
  public String getAttrStr(String attr) throws Exception {
    Object o = getAttrVal(attr);

    if (o == null) {
      return null;
    }

    if (o instanceof String) {
      return (String)o;
    }

    return o.toString();
  }

  public boolean getIsContent() {
    return isContentRec;
  }

  public int getChangeType() {
    return changeType;
  }

  public ModificationItem[] getMods() throws Exception {
    throw new Exception("Not a change record");
  }

  public void setIsContent(boolean val) {
    isContentRec = val;
  }

  public void setChangeType(int val) {
    changeType = val;
  }
}
