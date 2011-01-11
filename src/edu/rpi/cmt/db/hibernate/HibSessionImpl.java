/* **********************************************************************
    Copyright 2005 Rensselaer Polytechnic Institute. All worldwide rights reserved.

    Redistribution and use of this distribution in source and binary forms,
    with or without modification, are permitted provided that:
       The above copyright notice and this permission notice appear in all
        copies and supporting documentation;

        The name, identifiers, and trademarks of Rensselaer Polytechnic
        Institute are not used in advertising or publicity without the
        express prior written permission of Rensselaer Polytechnic Institute;

    DISCLAIMER: The software is distributed" AS IS" without any express or
    implied warranty, including but not limited to, any implied warranties
    of merchantability or fitness for a particular purpose or any warrant)'
    of non-infringement of any current or pending patent rights. The authors
    of the software make no representations about the suitability of this
    software for any particular purpose. The entire risk as to the quality
    and performance of the software is with the user. Should the software
    prove defective, the user assumes the cost of all necessary servicing,
    repair or correction. In particular, neither Rensselaer Polytechnic
    Institute, nor the authors of the software are liable for any indirect,
    special, consequential, or incidental damages related to the software,
    to the maximum extent the law permits.
*/
package edu.rpi.cmt.db.hibernate;

import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StaleStateException;
import org.hibernate.Transaction;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/** Convenience class to do the actual hibernate interaction. Intended for
 * one use only.
 *
 * @author Mike Douglass douglm@rpi.edu
 */
public class HibSessionImpl implements HibSession {
  transient Logger log;

  Session sess;
  transient Transaction tx;
  boolean rolledBack;

  transient Query q;
  transient Criteria crit;

  /** Exception from this session. */
  Throwable exc;

  private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

  /** Set up for a hibernate interaction. Throw the object away on exception.
   *
   * @param sessFactory
   * @param log
   * @throws HibException
   */
  public void init(final SessionFactory sessFactory,
                   final Logger log) throws HibException {
    try {
      this.log = log;
      sess = sessFactory.openSession();
      rolledBack = false;
      //sess.setFlushMode(FlushMode.COMMIT);
//      tx = sess.beginTransaction();
    } catch (Throwable t) {
      exc = t;
      tx = null;  // not even started. Should be null anyway
      close();
    }
  }

  public Session getSession() throws HibException {
    return sess;
  }

  /**
   * @return boolean true if open
   * @throws HibException
   */
  public boolean isOpen() throws HibException {
    try {
      if (sess == null) {
        return false;
      }
      return sess.isOpen();
    } catch (Throwable t) {
      handleException(t);
      return false;
    }
  }

  /** Clear a session
   *
   * @throws HibException
   */
  public void clear() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      sess.clear();
      tx =  null;
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Disconnect a session
   *
   * @throws HibException
   */
  public void disconnect() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      if (exc instanceof HibException) {
        throw (HibException)exc;
      }
      throw new HibException(exc);
    }

    try {
      sess.disconnect();
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** set the flushmode
   *
   * @param val
   * @throws HibException
   */
  public void setFlushMode(final FlushMode val) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      if (tx != null) {
        throw new HibException("Transaction already started");
      }

      sess.setFlushMode(val);
    } catch (Throwable t) {
      exc = t;
      throw new HibException(t);
    }
  }

  /** Begin a transaction
   *
   * @throws HibException
   */
  public void beginTransaction() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      if (tx != null) {
        throw new HibException("Transaction already started");
      }

      tx = sess.beginTransaction();
      rolledBack = false;
      if (tx == null) {
        throw new HibException("Transaction not started");
      }
    } catch (HibException cfe) {
      exc = cfe;
      throw cfe;
    } catch (Throwable t) {
      exc = t;
      throw new HibException(t);
    }
  }

  /** Return true if we have a transaction started
   *
   * @return boolean
   */
  public boolean transactionStarted() {
    return tx != null;
  }

  /** Commit a transaction
   *
   * @throws HibException
   */
  public void commit() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
//      if (tx != null &&
//          !tx.wasCommitted() &&
//          !tx.wasRolledBack()) {
        //if (getLogger().isDebugEnabled()) {
        //  getLogger().debug("About to comnmit");
        //}
      if (tx != null) {
        tx.commit();
      }

      tx = null;
    } catch (Throwable t) {
      exc = t;

      if (t instanceof StaleStateException) {
        throw new DbStaleStateException(t.getMessage());
      }
      throw new HibException(t);
    }
  }

  /** Rollback a transaction
   *
   * @throws HibException
   */
  public void rollback() throws HibException {
/*    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }
*/
    if (getLogger().isDebugEnabled()) {
      getLogger().debug("Enter rollback");
    }
    try {
      if ((tx != null) &&
          !tx.wasCommitted() &&
          !tx.wasRolledBack()) {
        if (getLogger().isDebugEnabled()) {
          getLogger().debug("About to rollback");
        }
        tx.rollback();
        //tx = null;
        clear();
        rolledBack = true;
      }
    } catch (Throwable t) {
      exc = t;
      throw new HibException(t);
    }
  }

  public boolean rolledback() throws HibException {
    return rolledBack;
  }

  /** Create a Criteria ready for the additon of Criterion.
   *
   * @param cl           Class for criteria
   * @return Criteria    created Criteria
   * @throws HibException
   */
  public Criteria createCriteria(final Class cl) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      crit = sess.createCriteria(cl);
      q = null;

      return crit;
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#evict(java.lang.Object)
   */
  public void evict(final Object val) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      sess.evict(val);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#createQuery(java.lang.String)
   */
  public void createQuery(final String s) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q = sess.createQuery(s);
      crit = null;
    } catch (Throwable t) {
      handleException(t);
    }
  }

  public void createNoFlushQuery(final String s) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q = sess.createQuery(s);
      crit = null;
      q.setFlushMode(FlushMode.COMMIT);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#getQueryString()
   */
  public String getQueryString() throws HibException {
    if (q == null) {
      return "*** no query ***";
    }

    try {
      return q.getQueryString();
    } catch (Throwable t) {
      handleException(t);
      return null;
    }
  }

  /** Create a sql query ready for parameter replacement or execution.
   *
   * @param s             String hibernate query
   * @param returnAlias
   * @param returnClass
   * @throws HibException
   */
  public void createSQLQuery(final String s, final String returnAlias, final Class returnClass)
        throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      SQLQuery sq = sess.createSQLQuery(s);
      sq.addEntity(returnAlias, returnClass);

      q = sq;
      crit = null;
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Create a named query ready for parameter replacement or execution.
   *
   * @param name         String named query name
   * @throws HibException
   */
  public void namedQuery(final String name) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q = sess.getNamedQuery(name);
      crit = null;
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Mark the query as cacheable
   *
   * @throws HibException
   */
  public void cacheableQuery() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setCacheable(true);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      String parameter value
   * @throws HibException
   */
  public void setString(final String parName, final String parVal) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setString(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      Date parameter value
   * @throws HibException
   */
  public void setDate(final String parName, final Date parVal) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      // Remove any time component
      synchronized (dateFormatter) {
        q.setDate(parName, java.sql.Date.valueOf(dateFormatter.format(parVal)));
      }
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      boolean parameter value
   * @throws HibException
   */
  public void setBool(final String parName, final boolean parVal) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setBoolean(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      int parameter value
   * @throws HibException
   */
  public void setInt(final String parName, final int parVal) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setInteger(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      long parameter value
   * @throws HibException
   */
  public void setLong(final String parName, final long parVal) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setLong(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Set the named parameter with the given value
   *
   * @param parName     String parameter name
   * @param parVal      Object parameter value
   * @throws HibException
   */
  public void setEntity(final String parName, final Object parVal) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setEntity(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#setParameter(java.lang.String, java.lang.Object)
   */
  public void setParameter(final String parName, final Object parVal) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setParameter(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#setParameterList(java.lang.String, java.util.Collection)
   */
  public void setParameterList(final String parName, final Collection parVal) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setParameterList(parName, parVal);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#setFirstResult(int)
   */
  public void setFirstResult(final int val) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setFirstResult(val);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#setMaxResults(int)
   */
  public void setMaxResults(final int val) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      q.setMaxResults(val);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#getUnique()
   */
  public Object getUnique() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      if (q != null) {
        return q.uniqueResult();
      }

      return crit.uniqueResult();
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /** Return a list resulting from the query.
   *
   * @return List          list from query
   * @throws HibException
   */
  public List getList() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      List l;
      if (q != null) {
        l = q.list();
      } else {
        l = crit.list();
      }

      if (l == null) {
        return new ArrayList();
      }

      return l;
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /**
   * @return int number updated
   * @throws HibException
   */
  public int executeUpdate() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      if (q == null) {
        throw new HibException("No query for execute update");
      }

      return q.executeUpdate();
    } catch (Throwable t) {
      handleException(t);
      return 0;  // Don't get here
    }
  }

  /** Update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   * @throws HibException
   */
  public void update(final Object obj) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      beforeSave(obj);
      sess.update(obj);
      deleteSubs(obj);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Merge and update an object which may have been loaded in a previous hibernate
   * session
   *
   * @param obj
   * @return Object   the persistent object
   * @throws HibException
   */
  public Object merge(Object obj) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      beforeSave(obj);

      obj = sess.merge(obj);
      deleteSubs(obj);

      return obj;
    } catch (Throwable t) {
      handleException(t, obj);
      return null;
    }
  }

  /** Save a new object or update an object which may have been loaded in a
   * previous hibernate session
   *
   * @param obj
   * @throws HibException
   */
  public void saveOrUpdate(final Object obj) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      beforeSave(obj);

      sess.saveOrUpdate(obj);
      deleteSubs(obj);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Copy the state of the given object onto the persistent object with the
   * same identifier. If there is no persistent instance currently associated
   * with the session, it will be loaded. Return the persistent instance.
   * If the given instance is unsaved or does not exist in the database,
   * save it and return it as a newly persistent instance. Otherwise, the
   * given instance does not become associated with the session.
   *
   * @param obj
   * @return Object
   * @throws HibException
   */
  public Object saveOrUpdateCopy(final Object obj) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      return sess.merge(obj);
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    A serializable key
   * @return Object
   * @throws HibException
   */
  public Object get(final Class cl, final Serializable id) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      return sess.get(cl, id);
    } catch (Throwable t) {
      handleException(t);
      return null;  // Don't get here
    }
  }

  /** Return an object of the given class with the given id if it is
   * already associated with this session. This must be called for specific
   * key queries or we can get a NonUniqueObjectException later.
   *
   * @param  cl    Class of the instance
   * @param  id    int key
   * @return Object
   * @throws HibException
   */
  public Object get(final Class cl, final int id) throws HibException {
    return get(cl, new Integer(id));
  }

  /** Save a new object.
   *
   * @param obj
   * @throws HibException
   */
  public void save(final Object obj) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      beforeSave(obj);
      sess.save(obj);
      deleteSubs(obj);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* * Save a new object with the given id. This should only be used for
   * restoring the db from a save or for assigned keys.
   *
   * @param obj
   * @param id
   * @throws HibException
   * /
  public void save(Object obj, Serializable id) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      sess.save(obj, id);
    } catch (Throwable t) {
      handleException(t);
    }
  }*/

  /** Delete an object
   *
   * @param obj
   * @throws HibException
   */
  public void delete(final Object obj) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      beforeDelete(obj);

      sess.delete(obj);
      deleteSubs(obj);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /** Save a new object with the given id. This should only be used for
   * restoring the db from a save.
   *
   * @param obj
   * @throws HibException
   */
  public void restore(final Object obj) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      sess.replicate(obj, ReplicationMode.IGNORE);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.HibSession#reAttach(org.bedework.calfacade.base.BwUnversionedDbentity)
   */
  public void reAttach(final UnversionedDbentity<?, ?> val) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      if (!val.unsaved()) {
        sess.lock(val, LockMode.NONE);
      }
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @param o
   * @throws HibException
   */
  public void lockRead(final Object o) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      sess.lock(o, LockMode.READ);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @param o
   * @throws HibException
   */
  public void lockUpdate(final Object o) throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    try {
      sess.lock(o, LockMode.UPGRADE);
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @throws HibException
   */
  public void flush() throws HibException {
    if (exc != null) {
      // Didn't hear me last time?
      throw new HibException(exc);
    }

    if (getLogger().isDebugEnabled()) {
      getLogger().debug("About to flush");
    }
    try {
      sess.flush();
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @throws HibException
   */
  public void close() throws HibException {
    if (sess == null) {
      return;
    }

//    throw new HibException("XXXXXXXXXXXXXXXXXXXXXXXXXXXXX");/*
    try {
      if (sess.isDirty()) {
        sess.flush();
      }
      if ((tx != null) && !rolledback()) {
        tx.commit();
      }
    } catch (Throwable t) {
      if (exc == null) {
        exc = t;
      }
    } finally {
      tx = null;
      if (sess != null) {
        try {
          sess.close();
        } catch (Throwable t) {}
      }
    }

    sess = null;
    if (exc != null) {
      throw new HibException(exc);
    }
//    */
  }

  private void handleException(final Throwable t) throws HibException {
    handleException(t, null);
  }

  private void handleException(final Throwable t,
                               final Object o) throws HibException {
    try {
      if (getLogger().isDebugEnabled()) {
        getLogger().debug("handleException called");
        if (o != null) {
          getLogger().debug(o.toString());
        }
        getLogger().error(this, t);
      }
    } catch (Throwable dummy) {}

    try {
      if (tx != null) {
        try {
          tx.rollback();
        } catch (Throwable t1) {
          rollbackException(t1);
        }
        tx = null;
      }
    } finally {
      try {
        sess.close();
      } catch (Throwable t2) {}
      sess = null;
    }

    exc = t;

    if (t instanceof StaleStateException) {
      throw new DbStaleStateException(t.getMessage());
    }

    throw new HibException(t);
  }

  private void beforeSave(final Object o) throws HibException {
    if (!(o instanceof VersionedDbEntity)) {
      return;
    }

    VersionedDbEntity ent = (VersionedDbEntity)o;

    ent.beforeSave();
  }

  private void beforeDelete(final Object o) throws HibException {
    if (!(o instanceof VersionedDbEntity)) {
      return;
    }

    VersionedDbEntity ent = (VersionedDbEntity)o;

    ent.beforeDeletion();
  }

  private void deleteSubs(final Object o) throws HibException {
    if (!(o instanceof VersionedDbEntity)) {
      return;
    }

    VersionedDbEntity ent = (VersionedDbEntity)o;

    Collection<VersionedDbEntity> subs = ent.getDeletedEntities();
    if (subs == null) {
      return;
    }

    for (VersionedDbEntity sub: subs) {
      delete(sub);
    }
  }

  /** This is just in case we want to report rollback exceptions. Seems we're
   * likely to get one.
   *
   * @param t   Throwable from the rollback
   */
  private void rollbackException(final Throwable t) {
    if (getLogger().isDebugEnabled()) {
      getLogger().debug("HibSession: ", t);
    }
    getLogger().error(this, t);
  }

  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}