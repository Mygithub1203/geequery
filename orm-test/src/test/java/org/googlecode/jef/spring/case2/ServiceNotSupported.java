package org.googlecode.jef.spring.case2;

import java.util.List;

import jef.database.jpa.JefEntityManager;

import org.easyframe.enterprise.spring.BaseDao;
import org.easyframe.enterprise.spring.CommonDaoImpl;
import org.googlecode.jef.spring.JefTransactionTest.DbCall;
import org.googlecode.jef.spring.entity.Tt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Repository()
@SuppressWarnings("unused")
public class ServiceNotSupported extends BaseDao{
	@Autowired
	private ServiceMandatory serviceMandatory;
	@Autowired
	private ServiceNested serviceNested;
	@Autowired
	private ServiceNever serviceNever;
	private ServiceNotSupported serviceNotSupported=this;
	@Autowired
	private ServiceRequired serviceRequired;
	@Autowired
	private ServiceRequiresNew serviceRequiresNew;
	@Autowired
	private ServiceSupports serviceSupports;
	@Autowired
	private CommonDaoImpl commondao;
	
	
	public Tt executeMethod1(List<Propagation> tasks,List<DbCall> calls) {
		if(!calls.isEmpty()){
			calls.remove(0).call((JefEntityManager) super.getEntityManager());
		}
		if(!tasks.isEmpty()){
			switch(tasks.remove(0)){
			case MANDATORY:
				serviceMandatory.executeMethod1(tasks,calls);
				break;
			case NESTED:
				serviceNested.executeMethod1(tasks,calls);
				break;
			case NEVER:
				serviceNever.executeMethod1(tasks,calls);
				break;
			case NOT_SUPPORTED:
				serviceNotSupported.executeMethod1(tasks,calls);
				break;
			case REQUIRED:
				serviceRequired.executeMethod1(tasks,calls);
				break;
			case REQUIRES_NEW:
				serviceRequiresNew.executeMethod1(tasks,calls);
				break;
			case SUPPORTS:
				serviceSupports.executeMethod1(tasks,calls);
				break;
			default:
				throw new RuntimeException();
					
			}
		}
		return null;
	}

}
